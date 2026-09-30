package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.health.service.dto.DailyMetricValue;
import com.sixtymeters.thereabout.health.service.dto.HealthDataResponse;
import com.sixtymeters.thereabout.health.service.dto.WorkoutSummary;
import com.sixtymeters.thereabout.health.data.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthQueryService {
    private final HealthMetricRepository healthMetricRepository;
    private final HealthMetricSleepRepository sleepRepository;
    private final WorkoutRepository workoutRepository;

    public HealthDataResponse getHealthData(com.sixtymeters.thereabout.access.UserId user, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) {
            throw new IllegalArgumentException("fromDate cannot be null");
        }
        if (toDate == null) {
            toDate = fromDate;
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate cannot be before fromDate");
        }

        // Retrieve all health metrics for the date range
        List<HealthMetricEntity> metrics = healthMetricRepository.findByUserIdAndMetricDateBetween(user.value(), fromDate, toDate);

        // Expose stored sleep stages and use totalSleep when the base quantity is absent.
        List<HealthMetricEntity> sleepMetrics = metrics.stream()
                .filter(m -> "sleep_analysis".equals(m.getMetricName()))
                .toList();
        Map<Long, HealthMetricSleepEntity> sleepDetailsByMetricId = sleepMetrics.isEmpty()
                ? Map.of()
                : sleepRepository.findByHealthMetricIn(sleepMetrics).stream()
                        .collect(Collectors.toMap(se -> se.getHealthMetric().getId(), se -> se, (a, b) -> a));

        final Map<Long, HealthMetricSleepEntity> sleepByMetricId = sleepDetailsByMetricId;
        // Group metrics by name and convert to DailyMetricValue
        Map<String, List<DailyMetricValue>> metricsMap = metrics.stream()
                .collect(Collectors.groupingBy(
                        HealthMetricEntity::getMetricName,
                        Collectors.mapping(
                                entity -> {
                                    HealthMetricSleepEntity sleep = sleepByMetricId.get(entity.getId());
                                    BigDecimal qty = "sleep_analysis".equals(entity.getMetricName()) && entity.getQty() == null
                                            ? (sleep == null ? null : sleep.getTotalSleep())
                                            : entity.getQty();
                                    return DailyMetricValue.builder()
                                            .date(entity.getMetricDate())
                                            .qty(qty)
                                            .core(sleep == null ? null : sleep.getCore())
                                            .deep(sleep == null ? null : sleep.getDeep())
                                            .rem(sleep == null ? null : sleep.getRem())
                                            .units(entity.getUnits())
                                            .timestamp(entity.getTimestamp())
                                            .source(entity.getSource())
                                            .build();
                                },
                                Collectors.toList()
                        )
                ));

        // Retrieve workouts for the date range (convert LocalDate to LocalDateTime range)
        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(23, 59, 59, 999999999);
        List<WorkoutEntity> workouts = workoutRepository.findByUserIdAndStartBetween(user.value(), fromDateTime, toDateTime);

        // Convert workouts to WorkoutSummary
        List<WorkoutSummary> workoutSummaries = workouts.stream()
                .map(workout -> WorkoutSummary.builder()
                        .id(workout.getSourceId())
                        .name(workout.getName())
                        .start(workout.getStart())
                        .end(workout.getEnd())
                        .duration(workout.getDuration())
                        .location(workout.getLocation())
                        .activeEnergyBurnedQty(workout.getActiveEnergyBurnedQty())
                        .activeEnergyBurnedUnits(workout.getActiveEnergyBurnedUnits())
                        .distanceQty(workout.getDistanceQty())
                        .distanceUnits(workout.getDistanceUnits())
                        .build())
                .collect(Collectors.toList());

        return HealthDataResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .metrics(metricsMap)
                .workouts(workoutSummaries)
                .build();
    }

}
