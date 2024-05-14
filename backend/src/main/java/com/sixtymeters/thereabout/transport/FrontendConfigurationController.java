package com.sixtymeters.thereabout.transport;

import com.sixtymeters.thereabout.domain.LocationHistoryService;
import com.sixtymeters.thereabout.generated.api.FrontendApi;
import com.sixtymeters.thereabout.generated.model.GenFrontendConfigurationResponse;
import com.sixtymeters.thereabout.support.ThereaboutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FrontendConfigurationController implements FrontendApi {

    @Value("${thereabout.apiKeys.googleMaps}")
    private String googleMapsApiKey;

    private final LocationHistoryService locationHistoryService;

    @Override
    public ResponseEntity<GenFrontendConfigurationResponse> getFrontendConfiguration() {
        log.info("Serving the frontend configuration.");
        return ResponseEntity.ok(GenFrontendConfigurationResponse.builder()
                .googleMapsApiKey(googleMapsApiKey)
                .build());
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
