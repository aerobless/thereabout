package com.sixtymeters.thereabout.client.transport;

import com.sixtymeters.thereabout.client.service.ConfigurationService;
import com.sixtymeters.thereabout.client.service.ImportProgressService;
import com.sixtymeters.thereabout.communication.service.importer.FileImporter;
import com.sixtymeters.thereabout.location.service.LocationHistoryService;
import com.sixtymeters.thereabout.generated.api.FrontendApi;
import com.sixtymeters.thereabout.generated.model.GenFileImportStatus;
import com.sixtymeters.thereabout.generated.model.GenFrontendConfigurationResponse;
import com.sixtymeters.thereabout.generated.model.GenImportType;
import com.sixtymeters.thereabout.generated.model.GenTelegramCodeRequest;
import com.sixtymeters.thereabout.generated.model.GenTelegramConnectRequest;
import com.sixtymeters.thereabout.generated.model.GenTelegramPasswordRequest;
import com.sixtymeters.thereabout.generated.model.GenTelegramStatus;
import com.sixtymeters.thereabout.generated.model.GenVersionDetails;
import com.sixtymeters.thereabout.communication.telegram.TelegramConnectionService;
import com.sixtymeters.thereabout.config.ThereaboutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.GitProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FrontendConfigurationController implements FrontendApi {

    @Value("${thereabout.apiKeys.googleMaps}")
    private String googleMapsApiKey;

    private final LocationHistoryService locationHistoryService;
    private final ConfigurationService configurationService;
    private final ImportProgressService importProgressService;
    private final GitProperties gitProperties;
    private final List<FileImporter> fileImporters;
    private final TelegramConnectionService telegramConnectionService;

    @Override
    public ResponseEntity<GenFileImportStatus> fileImportStatus() {
        final var importProgress = importProgressService.getProgress();

        return ResponseEntity.ok(GenFileImportStatus.builder()
                .status(mapImportProgressToStatus(importProgress))
                .progress(new BigDecimal(importProgress))
                .build());
    }

    private GenFileImportStatus.StatusEnum mapImportProgressToStatus(int importProgress) {
        return importProgress == 0 ? GenFileImportStatus.StatusEnum.IDLE : GenFileImportStatus.StatusEnum.IN_PROGRESS;
    }

    @Override
    public ResponseEntity<GenFrontendConfigurationResponse> getFrontendConfiguration() {
        log.info("Serving the frontend configuration.");
        return ResponseEntity.ok(GenFrontendConfigurationResponse.builder()
                .googleMapsApiKey(googleMapsApiKey)
                .thereaboutApiKey(configurationService.getThereaboutApiKey())
                .versionDetails(getVersionDetails())
                .build());
    }

    private GenVersionDetails getVersionDetails() {
        final var commitTime = gitProperties.getCommitTime().atOffset(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        final var version = commitTime.format(formatter);

        return GenVersionDetails.builder()
                .version(version)
                .commitTime(commitTime)
                .branch(gitProperties.getBranch())
                .commitRef(gitProperties.getShortCommitId())
                .build();
    }

    @Override
    public ResponseEntity<Void> importFromFile(MultipartFile file, GenImportType importType, String receiver) {
        log.info("Received file %s with import type %s via HTTP Endpoint /backend/api/v1/config/import-file".formatted(file.getOriginalFilename(), importType));

        final var importDataToBeProcessed = persistTempFileForProcessing(file);
        final var resolvedImportType = importType != null ? importType : GenImportType.GOOGLE_MAPS_RECORDS;

        if (resolvedImportType == GenImportType.GOOGLE_MAPS_RECORDS) {
            locationHistoryService.importGoogleLocationHistory(importDataToBeProcessed);
        } else {
            FileImporter importer = fileImporters.stream()
                    .filter(fi -> fi.getSupportedImportType() == resolvedImportType)
                    .findFirst()
                    .orElseThrow(() -> new ThereaboutException(HttpStatusCode.valueOf(400),
                            "No importer found for import type: %s".formatted(importType)));
            CompletableFuture.runAsync(() -> importer.importFile(importDataToBeProcessed, receiver));
        }

        return ResponseEntity.noContent().build();
    }

    private File persistTempFileForProcessing(MultipartFile file) {
        try {
            Path tempDir = Files.createTempDirectory("upload");
            File tempFile = new File(tempDir.toFile(), Objects.requireNonNull(file.getOriginalFilename()));
            file.transferTo(tempFile);
            log.info("Stored temporary file %s in %s".formatted(file.getName(), tempFile.getAbsolutePath()));
            return tempFile;
        } catch (IOException e) {
            throw new ThereaboutException(HttpStatusCode.valueOf(500), "Failed to create temporary directory for file upload: %s".formatted(e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<GenTelegramStatus> getTelegramStatus() {
        String status = telegramConnectionService.getStatus();
        GenTelegramStatus.StatusEnum statusEnum = mapToStatusEnum(status);
        Optional<com.sixtymeters.thereabout.communication.data.TelegramConnectionEntity> conn =
                telegramConnectionService.getConnection();
        GenTelegramStatus body = GenTelegramStatus.builder()
                .status(statusEnum)
                .phoneNumber(conn.map(com.sixtymeters.thereabout.communication.data.TelegramConnectionEntity::getPhoneNumber).orElse(null))
                .lastSyncAt(conn.flatMap(c -> c.getLastSyncAt() != null ? Optional.of(c.getLastSyncAt().atOffset(ZoneOffset.UTC)) : Optional.empty()).orElse(null))
                .configured(telegramConnectionService.isConfigured())
                .build();
        return ResponseEntity.ok(body);
    }

    private static GenTelegramStatus.StatusEnum mapToStatusEnum(String status) {
        try {
            return GenTelegramStatus.StatusEnum.fromValue(status != null ? status : "DISCONNECTED");
        } catch (IllegalArgumentException e) {
            return GenTelegramStatus.StatusEnum.DISCONNECTED;
        }
    }

    @Override
    public ResponseEntity<Void> connectTelegram(@Valid GenTelegramConnectRequest genTelegramConnectRequest) {
        String phone = genTelegramConnectRequest.getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            throw new ThereaboutException(HttpStatusCode.valueOf(400), "phoneNumber is required");
        }
        telegramConnectionService.connect(phone);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> submitTelegramCode(@Valid GenTelegramCodeRequest genTelegramCodeRequest) {
        String code = genTelegramCodeRequest.getCode();
        if (code != null) {
            telegramConnectionService.submitCode(code);
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> submitTelegramPassword(@Valid GenTelegramPasswordRequest genTelegramPasswordRequest) {
        String password = genTelegramPasswordRequest.getPassword();
        if (password != null) {
            telegramConnectionService.submitPassword(password);
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resyncTelegram() {
        telegramConnectionService.triggerResync();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> disconnectTelegram() {
        telegramConnectionService.disconnect();
        return ResponseEntity.noContent().build();
    }
}
