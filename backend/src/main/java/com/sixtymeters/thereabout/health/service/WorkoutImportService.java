package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.*;
import com.sixtymeters.thereabout.health.data.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutImportService {
    private final WorkoutRepository workoutRepository;
    private final WorkoutTimeSeriesDataRepository workoutTimeSeriesDataRepository;
    private final WorkoutMapper mapper;

    @Transactional
    public void saveWorkouts(List<GenWorkout> workouts) {
        if (workouts == null || workouts.isEmpty()) {
            return;
        }

        for (GenWorkout workout : workouts) {
            if (workout.getId() == null) {
                log.warn("Skipping workout with null id: {}", workout);
                continue;
            }

            WorkoutEntity workoutEntity = mapper.mapWorkoutToEntity(workout);
            workoutRepository.save(workoutEntity);

            // Delete existing time-series data and recreate
            workoutTimeSeriesDataRepository.deleteByWorkoutId(workout.getId());
            saveWorkoutTimeSeriesData(workout.getId(), workout);
        }
    }

    private void saveWorkoutTimeSeriesData(String workoutId, GenWorkout workout) {
        WorkoutEntity workoutEntity = workoutRepository.findById(workoutId).orElse(null);
        if (workoutEntity == null) {
            return;
        }

        List<WorkoutTimeSeriesDataEntity> timeSeriesData = new ArrayList<>();

        // Process all time-series arrays
        addTimeSeriesData(workoutEntity, workout.getActiveEnergy(), "activeEnergy", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getBasalEnergy(), "basalEnergy", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getCyclingCadence(), "cyclingCadence", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getCyclingDistance(), "cyclingDistance", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getCyclingPower(), "cyclingPower", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getCyclingSpeed(), "cyclingSpeed", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getSwimDistance(), "swimDistance", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getSwimStroke(), "swimStroke", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getStepCount(), "stepCount", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getWalkingAndRunningDistance(), "walkingAndRunningDistance", timeSeriesData);

        // Process heart rate data (has Min/Avg/Max structure)
        if (workout.getHeartRateData() != null) {
            for (GenWorkoutHeartRateData hrData : workout.getHeartRateData()) {
                OffsetDateTime date = hrData.getDate();
                WorkoutTimeSeriesDataEntity entity = WorkoutTimeSeriesDataEntity.builder()
                        .workout(workoutEntity)
                        .dataType("heartRateData")
                        .timestamp(date != null ? date.toLocalDateTime() : null)
                        .minValue(toBigDecimal(hrData.getMin()))
                        .avgValue(toBigDecimal(hrData.getAvg()))
                        .maxValue(toBigDecimal(hrData.getMax()))
                        .units(hrData.getUnits())
                        .build();
                timeSeriesData.add(entity);
            }
        }

        // Process heart rate recovery
        if (workout.getHeartRateRecovery() != null) {
            for (GenWorkoutHeartRateData hrData : workout.getHeartRateRecovery()) {
                OffsetDateTime date = hrData.getDate();
                WorkoutTimeSeriesDataEntity entity = WorkoutTimeSeriesDataEntity.builder()
                        .workout(workoutEntity)
                        .dataType("heartRateRecovery")
                        .timestamp(date != null ? date.toLocalDateTime() : null)
                        .minValue(toBigDecimal(hrData.getMin()))
                        .avgValue(toBigDecimal(hrData.getAvg()))
                        .maxValue(toBigDecimal(hrData.getMax()))
                        .units(hrData.getUnits())
                        .build();
                timeSeriesData.add(entity);
            }
        }

        if (!timeSeriesData.isEmpty()) {
            workoutTimeSeriesDataRepository.saveAll(timeSeriesData);
        }
    }

    private void addTimeSeriesData(WorkoutEntity workoutEntity, List<GenQuantityData> dataList, String dataType, List<WorkoutTimeSeriesDataEntity> result) {
        if (dataList == null) {
            return;
        }
        for (GenQuantityData data : dataList) {
            OffsetDateTime date = data.getDate();
            WorkoutTimeSeriesDataEntity entity = WorkoutTimeSeriesDataEntity.builder()
                    .workout(workoutEntity)
                    .dataType(dataType)
                    .timestamp(date != null ? date.toLocalDateTime() : null)
                    .qty(toBigDecimal(data.getQty()))
                    .units(data.getUnits())
                    .source(data.getSource())
                    .build();
            result.add(entity);
        }
    }

    private BigDecimal toBigDecimal(Number number) {
        if (number == null) {
            return null;
        }
        return BigDecimal.valueOf(number.doubleValue());
    }

}
