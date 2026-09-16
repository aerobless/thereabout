package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.GenHeartRateDay;
import com.sixtymeters.thereabout.generated.model.GenHeartRateHistory;
import com.sixtymeters.thereabout.health.data.HealthMetricEntity;
import com.sixtymeters.thereabout.health.data.HealthMetricHeartRateEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.sixtymeters.thereabout.health.service.HeartHistorySupport.*;

public final class HeartRateHistoryCalculator {
    private HeartRateHistoryCalculator() {}

    public static GenHeartRateHistory calculate(List<HealthMetricHeartRateEntity> records,
                                                List<HealthMetricEntity> restingRecords, LocalDate date, int days) {
        var daily = records.stream().filter(HeartRateHistoryCalculator::valid)
                .filter(record -> record.getHealthMetric().getMetricDate() != null
                        && !record.getHealthMetric().getMetricDate().isAfter(date))
                .collect(Collectors.groupingBy(record -> record.getHealthMetric().getMetricDate()));
        var resting = quantities(restingRecords, "resting_heart_rate", false, date);
        List<GenHeartRateDay> series = new ArrayList<>();
        for (LocalDate day = date.minusDays(days - 1L); !day.isAfter(date); day = day.plusDays(1)) {
            var values = daily.getOrDefault(day, List.of());
            var restingValues = resting.getOrDefault(day, List.of());
            List<HealthMetricEntity> imported = new ArrayList<>(restingValues);
            imported.addAll(values.stream().map(HealthMetricHeartRateEntity::getHealthMetric).toList());
            series.add(GenHeartRateDay.builder().date(day).recordCount(values.size()).restingRecordCount(restingValues.size())
                    .averageBpm(number(mean(values.stream().map(HealthMetricHeartRateEntity::getAvgValue).toList())))
                    .minimumBpm(values.stream().map(HealthMetricHeartRateEntity::getMinValue).min(java.util.Comparator.naturalOrder()).map(java.math.BigDecimal::doubleValue).orElse(null))
                    .maximumBpm(values.stream().map(HealthMetricHeartRateEntity::getMaxValue).max(java.util.Comparator.naturalOrder()).map(java.math.BigDecimal::doubleValue).orElse(null))
                    .restingBpm(number(mean(restingValues.stream().map(HealthMetricEntity::getQty).toList())))
                    .lastImportedAt(latestImport(imported)).build());
        }
        return GenHeartRateHistory.builder().date(date).selectedDay(series.getLast()).series(series).build();
    }

    private static boolean valid(HealthMetricHeartRateEntity record) {
        return record.getHealthMetric() != null && "heart_rate".equals(record.getHealthMetric().getMetricName())
                && unit(record.getHealthMetric(), false) && positive(record.getMinValue()) && positive(record.getAvgValue())
                && positive(record.getMaxValue()) && record.getMinValue().compareTo(record.getAvgValue()) <= 0
                && record.getAvgValue().compareTo(record.getMaxValue()) <= 0;
    }
}
