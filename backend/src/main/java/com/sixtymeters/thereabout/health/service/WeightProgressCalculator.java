package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.GenPreferences;
import com.sixtymeters.thereabout.generated.model.GenWeightDay;
import com.sixtymeters.thereabout.generated.model.GenWeightProgress;
import com.sixtymeters.thereabout.health.data.HealthMetricEntity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class WeightProgressCalculator {
    public static final List<String> METRICS = List.of("weight_body_mass", "weight", "body_mass");
    private static final BigDecimal THRESHOLD = new BigDecimal("0.1");
    private static final BigDecimal ZONE = new BigDecimal("0.5");
    private static final Comparator<HealthMetricEntity> READING_ORDER = Comparator
            .comparing(HealthMetricEntity::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparingInt(entry -> METRICS.indexOf(entry.getMetricName()))
            .thenComparing(HealthMetricEntity::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private WeightProgressCalculator() {}

    public static GenWeightProgress calculate(List<HealthMetricEntity> readings, GenPreferences preferences, LocalDate date, int days) {
        var daily = dailyReadings(readings, date);
        BigDecimal goal = preferences.getWeightGoalKg();
        LocalDate start = preferences.getWeightGoalStartedOn();
        Trend current = trend(daily, date);
        Trend previous = trend(daily, date.minusDays(7));
        BigDecimal gap = gap(current.value(), goal);
        BigDecimal previousGap = gap(previous.value(), goal);
        BigDecimal change = gap != null && previousGap != null ? gap.subtract(previousGap) : null;
        BigDecimal weightChange = current.value() != null && previous.value() != null ? current.value().subtract(previous.value()) : null;
        BigDecimal best = null;
        LocalDate bestDate = null;
        // Start no earlier than the first available reading; never look beyond the selected date.
        LocalDate first = daily.isEmpty() || start.isAfter(date) ? date : daily.firstKey().isAfter(start) ? daily.firstKey() : start;
        for (LocalDate cursor = first; !cursor.isBefore(start) && cursor.isBefore(date); cursor = cursor.plusDays(1)) {
            BigDecimal candidate = gap(trend(daily, cursor).value(), goal);
            if (candidate != null && (best == null || candidate.compareTo(best) < 0)) {
                best = candidate;
                bestDate = cursor;
            }
        }
        boolean newBest = best != null && gap != null && best.subtract(gap).compareTo(THRESHOLD) >= 0;
        GenWeightProgress.StateEnum state;
        if (date.isBefore(start)) state = GenWeightProgress.StateEnum.BEFORE_CHALLENGE;
        else if (gap == null) state = GenWeightProgress.StateEnum.NO_DATA;
        else if (gap.compareTo(ZONE) <= 0) state = GenWeightProgress.StateEnum.IN_ZONE;
        else if (newBest) state = GenWeightProgress.StateEnum.NEW_BEST;
        else if (change == null) state = GenWeightProgress.StateEnum.NO_DATA;
        else if (change.abs().compareTo(THRESHOLD) < 0) state = GenWeightProgress.StateEnum.STEADY;
        else if (change.signum() < 0) state = GenWeightProgress.StateEnum.TOWARD;
        else state = GenWeightProgress.StateEnum.AWAY;
        if (!date.isBefore(start) && gap != null && (best == null || gap.compareTo(best) < 0)) {
            best = gap;
            bestDate = date;
        }
        GenWeightProgress.ArrowEnum arrow = weightChange == null ? null : weightChange.abs().compareTo(THRESHOLD) < 0
                ? GenWeightProgress.ArrowEnum.STEADY : weightChange.signum() > 0 ? GenWeightProgress.ArrowEnum.UP : GenWeightProgress.ArrowEnum.DOWN;
        List<GenWeightDay> series = new ArrayList<>();
        for (LocalDate cursor = date.minusDays(days - 1L); !cursor.isAfter(date); cursor = cursor.plusDays(1)) {
            Trend point = trend(daily, cursor);
            series.add(GenWeightDay.builder().date(cursor).readingKg(number(daily.get(cursor)))
                    .trendKg(number(point.value())).coverage(point.coverage()).build());
        }
        Map.Entry<LocalDate, BigDecimal> latest = daily.lastEntry();
        return GenWeightProgress.builder().date(date).preferences(preferences)
                .zoneMinKg(goal.subtract(ZONE).doubleValue()).zoneMaxKg(goal.add(ZONE).doubleValue())
                .latestWeightKg(latest == null ? null : latest.getValue().doubleValue()).latestWeightDate(latest == null ? null : latest.getKey())
                .trendKg(number(current.value())).previousTrendKg(number(previous.value()))
                .coverage(current.coverage()).previousCoverage(previous.coverage())
                .gapKg(number(gap)).weeklyGapChangeKg(number(change)).bestGapKg(number(best)).bestDate(bestDate)
                .arrow(arrow).state(state).series(series).build();
    }

    static NavigableMap<LocalDate, BigDecimal> dailyReadings(List<HealthMetricEntity> readings, LocalDate date) {
        Map<LocalDate, HealthMetricEntity> selected = new TreeMap<>();
        for (var reading : readings) {
            if (!METRICS.contains(reading.getMetricName()) || reading.getMetricDate() == null || reading.getMetricDate().isAfter(date) || kilograms(reading) == null) continue;
            selected.merge(reading.getMetricDate(), reading, (left, right) -> READING_ORDER.compare(left, right) <= 0 ? left : right);
        }
        NavigableMap<LocalDate, BigDecimal> daily = new TreeMap<>();
        selected.forEach((key, value) -> daily.put(key, kilograms(value)));
        return daily;
    }

    private static BigDecimal kilograms(HealthMetricEntity reading) {
        BigDecimal value = reading.getQty();
        if (value == null || value.signum() <= 0) return null;
        String units = reading.getUnits() == null ? "kg" : reading.getUnits().trim().toLowerCase(Locale.ROOT);
        return switch (units) {
            case "", "kg", "kgs", "kilogram", "kilograms" -> value;
            case "lb", "lbs", "pound", "pounds" -> value.multiply(new BigDecimal("0.45359237"));
            default -> null;
        };
    }

    private static Trend trend(NavigableMap<LocalDate, BigDecimal> daily, LocalDate date) {
        var values = daily.subMap(date.minusDays(6), true, date, true).values();
        return new Trend(values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL128), values.size());
    }
    private static BigDecimal gap(BigDecimal weight, BigDecimal goal) { return weight == null ? null : weight.subtract(goal).abs(); }
    private static Double number(BigDecimal value) { return value == null ? null : value.doubleValue(); }
    private record Trend(BigDecimal value, int coverage) {}
}
