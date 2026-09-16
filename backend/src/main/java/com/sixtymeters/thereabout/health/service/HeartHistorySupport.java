package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.health.data.HealthMetricEntity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class HeartHistorySupport {
    private HeartHistorySupport() {}

    static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    static Double number(BigDecimal value) { return value == null ? null : value.doubleValue(); }
    static BigDecimal mean(Collection<BigDecimal> values) {
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL128);
    }
    static boolean unit(HealthMetricEntity metric, boolean hrv) {
        if (metric == null || metric.getUnits() == null) return false;
        String units = metric.getUnits().trim().toLowerCase(Locale.ROOT);
        return hrv ? List.of("ms", "millisecond", "milliseconds").contains(units)
                : List.of("count/min", "bpm", "beats/min").contains(units);
    }
    static OffsetDateTime latestImport(Collection<HealthMetricEntity> records) {
        return records.stream().flatMap(record -> java.util.stream.Stream.of(record.getCreatedAt(), record.getUpdatedAt()))
                .filter(java.util.Objects::nonNull).max(java.util.Comparator.naturalOrder())
                .map(instant -> instant.atOffset(ZoneOffset.UTC)).orElse(null);
    }
    static NavigableMap<LocalDate, List<HealthMetricEntity>> quantities(List<HealthMetricEntity> records,
                                                                         String metric, boolean hrv, LocalDate date) {
        return records.stream().filter(record -> metric.equals(record.getMetricName()) && record.getMetricDate() != null
                        && !record.getMetricDate().isAfter(date) && positive(record.getQty()) && unit(record, hrv))
                .collect(Collectors.groupingBy(HealthMetricEntity::getMetricDate, TreeMap::new, Collectors.toList()));
    }
}
