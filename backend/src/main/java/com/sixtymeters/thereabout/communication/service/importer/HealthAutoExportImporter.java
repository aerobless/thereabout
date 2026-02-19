package com.sixtymeters.thereabout.communication.service.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.client.service.ImportProgressService;
import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.generated.model.GenHealthData;
import com.sixtymeters.thereabout.generated.model.GenImportType;
import com.sixtymeters.thereabout.generated.model.GenSubmitHealthDataRequest;
import com.sixtymeters.thereabout.health.service.HealthDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Imports health data from Health Auto Export JSON files.
 * Reuses the same logic and tables as the REST health endpoint by calling {@link HealthDataService}.
 * Normalizes the export format (e.g. "speed" → "avgSpeed" for workouts) before deserialization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthAutoExportImporter implements FileImporter {

    private static final String DATA = "data";
    private static final String WORKOUTS = "workouts";
    private static final String SPEED = "speed";
    private static final String AVG_SPEED = "avgSpeed";

    private final HealthDataService healthDataService;
    private final ObjectMapper objectMapper;
    private final ImportProgressService importProgressService;

    @Override
    public GenImportType getSupportedImportType() {
        return GenImportType.HEALTH_AUTO_EXPORT_JSON;
    }

    @Override
    public void importFile(File file, String receiver) {
        log.info("Starting Health Auto Export import from file: {}", file.getName());
        importProgressService.setProgress(1);

        try {
            String content = Files.readString(file.toPath());
            JsonNode root = objectMapper.readTree(content);

            JsonNode dataNode = root.path(DATA);
            if (dataNode.isMissingNode()) {
                throw new ThereaboutException(HttpStatusCode.valueOf(400),
                        "Health Auto Export JSON must contain a 'data' object");
            }

            normalizeWorkoutSpeedToAvgSpeed(dataNode);

            GenSubmitHealthDataRequest request = objectMapper.treeToValue(root, GenSubmitHealthDataRequest.class);
            if (request == null || request.getData() == null) {
                throw new ThereaboutException(HttpStatusCode.valueOf(400),
                        "Health Auto Export JSON could not be parsed as health data");
            }

            GenHealthData healthData = request.getData();
            if (healthData.getMetrics() != null && !healthData.getMetrics().isEmpty()) {
                healthDataService.saveHealthMetrics(healthData.getMetrics());
                log.info("Saved {} health metrics from file", healthData.getMetrics().size());
                importProgressService.setProgress(50);
            }

            if (healthData.getWorkouts() != null && !healthData.getWorkouts().isEmpty()) {
                healthDataService.saveWorkouts(healthData.getWorkouts());
                log.info("Saved {} workouts from file", healthData.getWorkouts().size());
                importProgressService.setProgress(100);
            }
        } catch (IOException e) {
            throw new ThereaboutException(HttpStatusCode.valueOf(400),
                    "Failed to read Health Auto Export file '%s': %s".formatted(file.getName(), e.getMessage()));
        } finally {
            importProgressService.reset();
            cleanupTempFile(file);
        }

        log.info("Finished Health Auto Export import from file: {}", file.getName());
    }

    /**
     * Health Auto Export uses "speed" for workouts; the API expects "avgSpeed".
     * Mutates the data node so each workout has avgSpeed set from speed when avgSpeed is missing,
     * and removes "speed" so Jackson does not fail on the unknown property.
     */
    private void normalizeWorkoutSpeedToAvgSpeed(JsonNode dataNode) {
        JsonNode workouts = dataNode.path(WORKOUTS);
        if (!workouts.isArray()) {
            return;
        }
        for (JsonNode workout : workouts) {
            com.fasterxml.jackson.databind.node.ObjectNode obj = (com.fasterxml.jackson.databind.node.ObjectNode) workout;
            if (workout.has(SPEED) && !workout.has(AVG_SPEED)) {
                obj.set(AVG_SPEED, workout.get(SPEED));
            }
            obj.remove(SPEED);
        }
    }

    private void cleanupTempFile(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            log.warn("Failed to clean up temporary file: {}", file.getAbsolutePath(), e);
        }
    }
}
