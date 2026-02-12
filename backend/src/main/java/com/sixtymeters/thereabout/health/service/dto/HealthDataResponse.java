package com.sixtymeters.thereabout.health.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthDataResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Map<String, List<DailyMetricValue>> metrics;
    private List<WorkoutSummary> workouts;
}
