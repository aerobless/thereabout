package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.GenHrvDay;
import com.sixtymeters.thereabout.generated.model.GenHrvHistory;
import com.sixtymeters.thereabout.health.data.HealthMetricEntity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static com.sixtymeters.thereabout.health.service.HeartHistorySupport.*;

public final class HrvHistoryCalculator {
    private static final int MIN_WEEK_DAYS = 4;
    private static final int MIN_BASELINE_DAYS = 14;
    private HrvHistoryCalculator() {}

    public static GenHrvHistory calculate(List<HealthMetricEntity> records, LocalDate date, int days) {
        var imported = quantities(records, "heart_rate_variability", true, date);
        NavigableMap<LocalDate, BigDecimal> daily = new TreeMap<>();
        imported.forEach((day, values) -> daily.put(day, mean(values.stream().map(HealthMetricEntity::getQty).toList())));
        List<GenHrvDay> series = new ArrayList<>();
        for (LocalDate day = date.minusDays(days - 1L); !day.isAfter(date); day = day.plusDays(1)) {
            var week = week(daily, day);
            var baseline = daily.subMap(day.minusDays(30), true, day, false).values().stream().sorted().toList();
            var values = imported.getOrDefault(day, List.of());
            series.add(GenHrvDay.builder().date(day).averageMs(number(daily.get(day))).recordCount(values.size())
                    .trendMs(week.size() >= MIN_WEEK_DAYS ? number(mean(week)) : null).coverage(week.size())
                    .baselineCoverage(baseline.size()).baselineLowMs(percentile(baseline, 0.1)).baselineHighMs(percentile(baseline, 0.9))
                    .lastImportedAt(latestImport(values)).build());
        }
        var current = week(daily, date);
        var previous = week(daily, date.minusDays(7));
        BigDecimal currentMean = mean(current);
        BigDecimal previousMean = mean(previous);
        boolean enough = current.size() >= MIN_WEEK_DAYS && previous.size() >= MIN_WEEK_DAYS && positive(previousMean);
        BigDecimal change = enough ? currentMean.subtract(previousMean).multiply(BigDecimal.valueOf(100))
                .divide(previousMean, MathContext.DECIMAL128) : null;
        GenHrvHistory.StateEnum state;
        if (!daily.containsKey(date)) state = GenHrvHistory.StateEnum.NO_DATA;
        else if (!enough) state = GenHrvHistory.StateEnum.INSUFFICIENT_DATA;
        else if (currentMean.multiply(BigDecimal.valueOf(100)).compareTo(previousMean.multiply(BigDecimal.valueOf(105))) >= 0)
            state = GenHrvHistory.StateEnum.UP;
        else if (currentMean.multiply(BigDecimal.valueOf(100)).compareTo(previousMean.multiply(BigDecimal.valueOf(95))) <= 0)
            state = GenHrvHistory.StateEnum.DOWN;
        else state = GenHrvHistory.StateEnum.STEADY;
        return GenHrvHistory.builder().date(date).selectedDay(series.getLast()).series(series)
                .weeklyAverageMs(number(currentMean)).previousWeeklyAverageMs(number(previousMean))
                .coverage(current.size()).previousCoverage(previous.size()).changePercent(number(change)).state(state).build();
    }

    private static List<BigDecimal> week(NavigableMap<LocalDate, BigDecimal> daily, LocalDate date) {
        return new ArrayList<>(daily.subMap(date.minusDays(6), true, date, true).values());
    }
    private static Double percentile(List<BigDecimal> sorted, double percentile) {
        return sorted.size() < MIN_BASELINE_DAYS ? null : sorted.get((int) Math.ceil(percentile * sorted.size()) - 1).doubleValue();
    }
}
