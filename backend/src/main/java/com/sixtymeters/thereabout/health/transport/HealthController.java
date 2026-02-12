package com.sixtymeters.thereabout.health.transport;

import com.sixtymeters.thereabout.config.AuthorizationService;
import com.sixtymeters.thereabout.health.service.dto.DailyMetricValue;
import com.sixtymeters.thereabout.health.service.dto.HealthDataResponse;
import com.sixtymeters.thereabout.health.service.HealthDataService;
import com.sixtymeters.thereabout.health.service.dto.WorkoutSummary;
import com.sixtymeters.thereabout.generated.api.HealthApi;
import com.sixtymeters.thereabout.generated.model.GenDailyMetricValue;
import com.sixtymeters.thereabout.generated.model.GenHealthData;
import com.sixtymeters.thereabout.generated.model.GenHealthDataResponse;
import com.sixtymeters.thereabout.generated.model.GenSubmitHealthDataRequest;
import com.sixtymeters.thereabout.generated.model.GenWorkoutSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController implements HealthApi {

    private final HealthDataService healthDataService;
    private final AuthorizationService authorizationService;

    @Override
    public ResponseEntity<Void> submitHealthData(String authorization, GenSubmitHealthDataRequest genSubmitHealthDataRequest) {
        authorizationService.isAuthorised(authorization);
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

    @Override
    public ResponseEntity<GenHealthDataResponse> getHealthDataByDateRange(LocalDate fromDate, Optional<LocalDate> toDate) {
        try {
            LocalDate endDate = toDate.orElse(fromDate);
            HealthDataResponse domainResponse = healthDataService.getHealthData(fromDate, endDate);
            GenHealthDataResponse genResponse = convertToGenResponse(domainResponse);
            return ResponseEntity.ok(genResponse);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid date range: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error retrieving health data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private GenHealthDataResponse convertToGenResponse(HealthDataResponse domainResponse) {
        Map<String, List<GenDailyMetricValue>> genMetrics = domainResponse.getMetrics().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(this::convertToGenDailyMetricValue)
                                .collect(Collectors.toList())
                ));

        List<GenWorkoutSummary> genWorkouts = domainResponse.getWorkouts().stream()
                .map(this::convertToGenWorkoutSummary)
                .collect(Collectors.toList());

        return GenHealthDataResponse.builder()
                .fromDate(domainResponse.getFromDate())
                .toDate(domainResponse.getToDate())
                .metrics(genMetrics)
                .workouts(genWorkouts)
                .build();
    }

    private GenDailyMetricValue convertToGenDailyMetricValue(DailyMetricValue domainValue) {
        return GenDailyMetricValue.builder()
                .date(domainValue.getDate())
                .qty(domainValue.getQty())
                .units(domainValue.getUnits())
                .timestamp(domainValue.getTimestamp() != null ? domainValue.getTimestamp().atOffset(java.time.ZoneOffset.UTC) : null)
                .source(domainValue.getSource())
                .build();
    }

    private GenWorkoutSummary convertToGenWorkoutSummary(WorkoutSummary domainWorkout) {
        return GenWorkoutSummary.builder()
                .id(domainWorkout.getId())
                .name(domainWorkout.getName())
                .start(domainWorkout.getStart() != null ? domainWorkout.getStart().atOffset(java.time.ZoneOffset.UTC) : null)
                .end(domainWorkout.getEnd() != null ? domainWorkout.getEnd().atOffset(java.time.ZoneOffset.UTC) : null)
                .duration(domainWorkout.getDuration())
                .location(domainWorkout.getLocation())
                .activeEnergyBurnedQty(domainWorkout.getActiveEnergyBurnedQty())
                .activeEnergyBurnedUnits(domainWorkout.getActiveEnergyBurnedUnits())
                .distanceQty(domainWorkout.getDistanceQty())
                .distanceUnits(domainWorkout.getDistanceUnits())
                .build();
    }
}
