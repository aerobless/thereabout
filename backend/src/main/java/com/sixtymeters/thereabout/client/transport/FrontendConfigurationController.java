package com.sixtymeters.thereabout.client.transport;

import com.sixtymeters.thereabout.client.service.ConfigurationService;
import com.sixtymeters.thereabout.client.service.ImportProgressService;
import com.sixtymeters.thereabout.client.service.FileImportService;
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

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FrontendConfigurationController implements FrontendApi {

    @Value("${thereabout.apiKeys.googleMaps}")
    private String googleMapsApiKey;

    private final FileImportService fileImportService;
    private final ConfigurationService configurationService;
    private final ImportProgressService importProgressService;
    private final GitProperties gitProperties;
    private final TelegramConnectionService telegramConnectionService;

    @Override
    public ResponseEntity<GenFileImportStatus> fileImportStatus() {
        var snapshot = importProgressService.snapshot();
        return ResponseEntity.ok(GenFileImportStatus.builder()
                .status(GenFileImportStatus.StatusEnum.valueOf(snapshot.status().name()))
                .progress(new BigDecimal(snapshot.progress()))
                .error(snapshot.error())
                .build());
    }

    @Override
    public ResponseEntity<GenFrontendConfigurationResponse> getFrontendConfiguration() {
        log.info("Serving the frontend configuration.");
        return ResponseEntity.ok(GenFrontendConfigurationResponse.builder()
                .googleMapsApiKey(googleMapsApiKey)
                .versionDetails(getVersionDetails())
                .build());
    }

    @Override
    public ResponseEntity<com.sixtymeters.thereabout.generated.model.GenGetIngestionKey200Response> getIngestionKey() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new com.sixtymeters.thereabout.generated.model.GenGetIngestionKey200Response().value(configurationService.getThereaboutApiKey()));
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
    public ResponseEntity<Void> importFromFile(MultipartFile file, GenImportType importType, Optional<String> receiver) {
        fileImportService.start(file, importType, receiver);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GenTelegramStatus> getTelegramStatus() {
        String status = telegramConnectionService.getStatus();
        GenTelegramStatus.StatusEnum statusEnum = mapToStatusEnum(status);
        Optional<com.sixtymeters.thereabout.communication.data.TelegramConnectionEntity> conn =
                telegramConnectionService.getConnection();
        var bodyBuilder = GenTelegramStatus.builder()
                .status(statusEnum)
                .phoneNumber(conn.map(com.sixtymeters.thereabout.communication.data.TelegramConnectionEntity::getPhoneNumber).orElse(null))
                .lastSyncAt(conn.flatMap(c -> c.getLastSyncAt() != null ? Optional.of(c.getLastSyncAt().atOffset(ZoneOffset.UTC)) : Optional.empty()).orElse(null))
                .configured(telegramConnectionService.isConfigured())
                .resyncProgress(telegramConnectionService.getResyncProgress());
        String resyncStatusStr = telegramConnectionService.getResyncStatus();
        if (resyncStatusStr != null) {
            try {
                bodyBuilder.resyncStatus(GenTelegramStatus.ResyncStatusEnum.fromValue(resyncStatusStr));
            } catch (IllegalArgumentException ignored) {
                // leave resyncStatus null if unknown
            }
        }
        return ResponseEntity.ok(bodyBuilder.build());
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
    public ResponseEntity<Void> cancelTelegramResync() {
        telegramConnectionService.cancelResync();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> disconnectTelegram() {
        telegramConnectionService.disconnect();
        return ResponseEntity.noContent().build();
    }
}
