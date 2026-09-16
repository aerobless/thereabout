package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.GenHrvHistory;
import com.sixtymeters.thereabout.health.data.HealthMetricEntity;
import com.sixtymeters.thereabout.health.data.HealthMetricHeartRateEntity;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HeartHistoryCalculatorTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 16);
    private HealthMetricEntity quantity(LocalDate date, String name, String value, String unit) {
        return HealthMetricEntity.builder().metricDate(date).metricName(name).units(unit)
                .qty(value == null ? null : new BigDecimal(value)).createdAt(Instant.parse("2026-09-16T12:00:00Z")).build();
    }
    private HealthMetricEntity hrv(LocalDate date, String value) { return quantity(date, "heart_rate_variability", value, "ms"); }
    private HealthMetricHeartRateEntity heart(LocalDate date, String min, String avg, String max) {
        return HealthMetricHeartRateEntity.builder().healthMetric(quantity(date, "heart_rate", null, "count/min"))
                .minValue(new BigDecimal(min)).avgValue(new BigDecimal(avg)).maxValue(new BigDecimal(max)).build();
    }
    private List<HealthMetricEntity> weeks(String previous, String current) {
        List<HealthMetricEntity> records = new ArrayList<>();
        for (int i = 0; i < 14; i++) records.add(hrv(DAY.minusDays(i), i < 7 ? current : previous));
        return records;
    }
    @Test void heartRateUsesDetailSummariesAndArithmeticMeanWithIndependentRestingValues() {
        var resting = quantity(DAY, "resting_heart_rate", "55", "bpm");
        resting.setUpdatedAt(Instant.parse("2026-09-16T13:00:00Z"));
        var result = HeartRateHistoryCalculator.calculate(List.of(heart(DAY, "48", "70.5", "130"), heart(DAY, "50", "80.5", "150")),
                List.of(resting, quantity(DAY, "resting_heart_rate", "57", "count/min")), DAY, 7);
        assertThat(result.getSelectedDay().getAverageBpm()).isEqualTo(75.5);
        assertThat(result.getSelectedDay().getMinimumBpm()).isEqualTo(48);
        assertThat(result.getSelectedDay().getMaximumBpm()).isEqualTo(150);
        assertThat(result.getSelectedDay().getRestingBpm()).isEqualTo(56);
        assertThat(result.getSelectedDay().getRecordCount()).isEqualTo(2);
        assertThat(result.getSelectedDay().getRestingRecordCount()).isEqualTo(2);
        assertThat(result.getSelectedDay().getLastImportedAt().toInstant()).isEqualTo(resting.getUpdatedAt());
        assertThat(result.getSeries()).hasSize(7);
        assertThat(result.getSeries().getFirst().getAverageBpm()).isNull();
        assertThat(result.getSeries().getFirst().getRecordCount()).isZero();
    }
    @Test void invalidHeartRateSummariesAndUnitsNeverBecomeZeroReadings() {
        var unknown = heart(DAY, "40", "60", "100"); unknown.getHealthMetric().setUnits("Hz");
        var missing = heart(DAY, "40", "60", "100"); missing.setMaxValue(null);
        var nullDate = heart(DAY, "40", "60", "100"); nullDate.getHealthMetric().setMetricDate(null);
        var result = HeartRateHistoryCalculator.calculate(List.of(heart(DAY, "0", "60", "100"), heart(DAY, "70", "60", "100"),
                heart(DAY, "40", "110", "100"), unknown, missing, nullDate, heart(DAY.plusDays(1), "40", "60", "100")),
                List.of(quantity(DAY, "resting_heart_rate", "-1", "bpm"), quantity(DAY, "resting_heart_rate", "60", "unknown")), DAY, 30);
        assertThat(result.getSelectedDay().getAverageBpm()).isNull();
        assertThat(result.getSelectedDay().getMinimumBpm()).isNull();
        assertThat(result.getSelectedDay().getRestingBpm()).isNull();
        assertThat(result.getSelectedDay().getRecordCount()).isZero();
        assertThat(result.getSeries()).hasSize(30);
    }
    @Test void exactFivePercentBoundariesAndUnroundedNeutralChanges() {
        assertThat(HrvHistoryCalculator.calculate(weeks("40", "42"), DAY, 7).getState()).isEqualTo(GenHrvHistory.StateEnum.UP);
        assertThat(HrvHistoryCalculator.calculate(weeks("40", "38"), DAY, 7).getState()).isEqualTo(GenHrvHistory.StateEnum.DOWN);
        for (String current : List.of("41.999", "38.001", "40"))
            assertThat(HrvHistoryCalculator.calculate(weeks("40", current), DAY, 7).getState()).isEqualTo(GenHrvHistory.StateEnum.STEADY);
        assertThat(HrvHistoryCalculator.calculate(weeks("40", "42"), DAY, 7).getChangePercent()).isEqualTo(5);
    }
    @Test void averagesEachDayBeforeAveragingTheWeek() {
        var records = weeks("40", "40"); records.add(hrv(DAY, "80"));
        var result = HrvHistoryCalculator.calculate(records, DAY, 7);
        assertThat(result.getSelectedDay().getAverageMs()).isEqualTo(60);
        assertThat(result.getSelectedDay().getRecordCount()).isEqualTo(2);
        assertThat(result.getWeeklyAverageMs()).isCloseTo(300.0 / 7, within(1e-10));
        assertThat(result.getState()).isEqualTo(GenHrvHistory.StateEnum.UP);
    }
    @Test void fourRecordedDaysInBothCalendarWindowsAreRequired() {
        var records = new ArrayList<HealthMetricEntity>();
        for (int i : List.of(0, 1, 3, 6, 7, 8, 10, 13)) records.add(hrv(DAY.minusDays(i), i < 7 ? "50" : "40"));
        var enough = HrvHistoryCalculator.calculate(records, DAY, 7);
        assertThat(enough.getCoverage()).isEqualTo(4);
        assertThat(enough.getPreviousCoverage()).isEqualTo(4);
        assertThat(enough.getState()).isEqualTo(GenHrvHistory.StateEnum.UP);
        records.removeLast();
        var sparse = HrvHistoryCalculator.calculate(records, DAY, 7);
        assertThat(sparse.getState()).isEqualTo(GenHrvHistory.StateEnum.INSUFFICIENT_DATA);
        assertThat(sparse.getChangePercent()).isNull();
        assertThat(sparse.getWeeklyAverageMs()).isEqualTo(50);
        records.removeIf(record -> record.getMetricDate().isAfter(DAY.minusDays(3)) && !record.getMetricDate().equals(DAY));
        assertThat(HrvHistoryCalculator.calculate(records, DAY, 7).getSelectedDay().getTrendMs()).isNull();
    }
    @Test void missingSelectedDaySuppressesColourAndZeroDenominatorsAreExcluded() {
        var missing = weeks("40", "50"); missing.removeFirst();
        assertThat(HrvHistoryCalculator.calculate(missing, DAY, 7).getState()).isEqualTo(GenHrvHistory.StateEnum.NO_DATA);
        var zero = HrvHistoryCalculator.calculate(weeks("0", "50"), DAY, 7);
        assertThat(zero.getPreviousCoverage()).isZero();
        assertThat(zero.getChangePercent()).isNull();
        assertThat(zero.getState()).isEqualTo(GenHrvHistory.StateEnum.INSUFFICIENT_DATA);
        var invalid = hrv(DAY, "99"); invalid.setUnits("s");
        var empty = HrvHistoryCalculator.calculate(List.of(invalid, hrv(DAY, null), hrv(DAY, "-5")), DAY, 30);
        assertThat(empty.getSelectedDay().getAverageMs()).isNull();
        assertThat(empty.getSeries()).allMatch(day -> day.getAverageMs() == null && day.getTrendMs() == null && day.getRecordCount() == 0);
    }
    @Test void personalRangeUsesNearestRankOfPrecedingDaysWithFourteenDayWarmup() {
        var records = new ArrayList<HealthMetricEntity>();
        for (int i = 1; i <= 14; i++) records.add(hrv(DAY.minusDays(i), Integer.toString(i)));
        records.add(hrv(DAY, "999"));
        var result = HrvHistoryCalculator.calculate(records, DAY, 7);
        assertThat(result.getSelectedDay().getBaselineCoverage()).isEqualTo(14);
        assertThat(result.getSelectedDay().getBaselineLowMs()).isEqualTo(2);
        assertThat(result.getSelectedDay().getBaselineHighMs()).isEqualTo(13);
        assertThat(result.getSeries().get(5).getBaselineLowMs()).isNull();
        records.add(hrv(DAY.minusDays(31), "10000"));
        assertThat(HrvHistoryCalculator.calculate(records, DAY, 7).getSelectedDay().getBaselineHighMs()).isEqualTo(13);
    }
    @Test void historicalValuesNeverUseLaterDaysAndRangeDoesNotChangeSummary() {
        var records = weeks("40", "45");
        var expected = HrvHistoryCalculator.calculate(records, DAY, 7);
        records.add(hrv(DAY.plusDays(1), "999"));
        var longer = HrvHistoryCalculator.calculate(records, DAY, 30);
        assertThat(longer.getSelectedDay()).isEqualTo(expected.getSelectedDay());
        assertThat(longer.getState()).isEqualTo(expected.getState());
        assertThat(longer.getWeeklyAverageMs()).isEqualTo(expected.getWeeklyAverageMs());
        assertThat(longer.getPreviousWeeklyAverageMs()).isEqualTo(expected.getPreviousWeeklyAverageMs());
        assertThat(longer.getChangePercent()).isEqualTo(expected.getChangePercent());
        var heart = List.of(heart(DAY, "45", "70", "120"));
        assertThat(HeartRateHistoryCalculator.calculate(heart, List.of(), DAY, 7).getSelectedDay())
                .isEqualTo(HeartRateHistoryCalculator.calculate(heart, List.of(), DAY, 30).getSelectedDay());
    }
    @Test void dayBoundariesUseStoredMetricDateEvenAcrossLeapDays() {
        LocalDate date = LocalDate.of(2024, 3, 1);
        var record = hrv(date.minusDays(1), "50"); record.setTimestamp(date.atTime(1, 0));
        var result = HrvHistoryCalculator.calculate(List.of(record), date, 7);
        assertThat(result.getSeries().get(5).getDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(result.getSeries().get(5).getAverageMs()).isEqualTo(50);
        assertThat(result.getSelectedDay().getAverageMs()).isNull();
    }
}
