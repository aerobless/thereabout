package com.sixtymeters.thereabout.client.transport;

import com.sixtymeters.thereabout.client.service.ConfigurationService;
import com.sixtymeters.thereabout.location.service.LocationHistoryService;
import com.sixtymeters.thereabout.generated.api.FrontendApi;
import com.sixtymeters.thereabout.generated.model.GenFileImportStatus;
import com.sixtymeters.thereabout.generated.model.GenFrontendConfigurationResponse;
import com.sixtymeters.thereabout.generated.model.GenVersionDetails;
import com.sixtymeters.thereabout.config.ThereaboutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.GitProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FrontendConfigurationController implements FrontendApi {

    @Value("${thereabout.apiKeys.googleMaps}")
    private String googleMapsApiKey;

    private final LocationHistoryService locationHistoryService;
    private final ConfigurationService configurationService;
    private final GitProperties gitProperties;

    @Override
    public ResponseEntity<GenFileImportStatus> fileImportStatus() {
        final var importProgress = locationHistoryService.getImportProgress();

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
    public ResponseEntity<Void> importFromFile(MultipartFile file) {
        log.info("Received file %s via HTTP Endpoint /backend/api/v1/config/import-file".formatted(file.getOriginalFilename()));

        final var importDataToBeProcessed = persistTempFileForProcessing(file);
        locationHistoryService.importGoogleLocationHistory(importDataToBeProcessed);

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


}
