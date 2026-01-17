package com.sixtymeters.thereabout.transport.health;

import com.sixtymeters.thereabout.domain.health.HealthDataService;
import com.sixtymeters.thereabout.generated.api.HealthApi;
import com.sixtymeters.thereabout.generated.model.GenHealthData;
import com.sixtymeters.thereabout.generated.model.GenSubmitHealthDataRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController implements HealthApi {

    private final HealthDataService healthDataService;

    @Override
    public ResponseEntity<Void> submitHealthData(GenSubmitHealthDataRequest genSubmitHealthDataRequest) {
        log.info("Received health data submission");

        if (genSubmitHealthDataRequest == null || genSubmitHealthDataRequest.getData() == null) {
            log.warn("Health data request is null or missing data field");
            return ResponseEntity.badRequest().build();
        }

        GenHealthData healthData = genSubmitHealthDataRequest.getData();

        try {
            if (healthData.getMetrics() != null && !healthData.getMetrics().isEmpty()) {
                healthDataService.saveHealthMetrics(healthData.getMetrics());
                log.info("Saved {} health metrics", healthData.getMetrics().size());
            }

            if (healthData.getWorkouts() != null && !healthData.getWorkouts().isEmpty()) {
                healthDataService.saveWorkouts(healthData.getWorkouts());
                log.info("Saved {} workouts", healthData.getWorkouts().size());
            }

            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (Exception e) {
            log.error("Error saving health data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
