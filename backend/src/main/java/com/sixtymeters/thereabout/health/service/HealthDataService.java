package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.GenHealthMetric;
import com.sixtymeters.thereabout.generated.model.GenWorkout;
import com.sixtymeters.thereabout.health.service.dto.HealthDataResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

/** Shared entry point for HTTP ingestion and file imports; each writer owns its transaction. */
@Service
@RequiredArgsConstructor
public class HealthDataService {
    private final HealthMetricImportService metrics;
    private final WorkoutImportService workouts;
    private final HealthQueryService queries;

    public void saveHealthMetrics(List<GenHealthMetric> input) { metrics.saveHealthMetrics(input); }
    public void saveWorkouts(List<GenWorkout> input) { workouts.saveWorkouts(input); }
    public HealthDataResponse getHealthData(LocalDate from, LocalDate to) { return queries.getHealthData(from, to); }
}
