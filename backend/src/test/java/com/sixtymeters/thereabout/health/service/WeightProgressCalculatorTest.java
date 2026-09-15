package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.GenPreferences;
import com.sixtymeters.thereabout.generated.model.GenWeightProgress;
import com.sixtymeters.thereabout.health.data.HealthMetricEntity;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WeightProgressCalculatorTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 15);
    private HealthMetricEntity reading(LocalDate day, String qty) {
        return HealthMetricEntity.builder().metricDate(day).timestamp(day.atTime(8, 0))
                .metricName("weight_body_mass").qty(new BigDecimal(qty)).units("kg").build();
    }
    private GenPreferences prefs(String goal, LocalDate start) {
        return GenPreferences.builder().weightGoalKg(new BigDecimal(goal)).weightGoalStartedOn(start).build();
    }
    private GenWeightProgress weekly(String previous, String current, String goal) {
        return WeightProgressCalculator.calculate(List.of(reading(DAY.minusDays(7), previous), reading(DAY, current)), prefs(goal, DAY), DAY, 30);
    }

    @Test void gainingTowardHigherGoalIsGreenWithUpArrow() {
        var result = weekly("68", "69", "75");
        assertThat(result.getState()).isEqualTo(GenWeightProgress.StateEnum.TOWARD);
        assertThat(result.getArrow()).isEqualTo(GenWeightProgress.ArrowEnum.UP);
        assertThat(result.getWeeklyGapChangeKg()).isEqualTo(-1);
    }
    @Test void losingAwayFromGoalIsCoralWithDownArrow() {
        var result = weekly("72", "71", "75");
        assertThat(result.getState()).isEqualTo(GenWeightProgress.StateEnum.AWAY);
        assertThat(result.getArrow()).isEqualTo(GenWeightProgress.ArrowEnum.DOWN);
        assertThat(result.getWeeklyGapChangeKg()).isEqualTo(1);
    }
    @Test void lossTowardGoalAndGainAwayHaveIndependentArrows() {
        assertThat(weekly("82", "81", "75").getState()).isEqualTo(GenWeightProgress.StateEnum.TOWARD);
        assertThat(weekly("81", "82", "75").getState()).isEqualTo(GenWeightProgress.StateEnum.AWAY);
    }
    @Test void inclusiveZoneOverridesOtherStates() {
        for (String weight : List.of("74.5", "75", "75.5")) {
            assertThat(weekly("76", weight, "75").getState()).isEqualTo(GenWeightProgress.StateEnum.IN_ZONE);
        }
        assertThat(weekly("74.5", "74.49", "75").getState()).isEqualTo(GenWeightProgress.StateEnum.STEADY);
        assertThat(weekly("75.5", "75.51", "75").getState()).isEqualTo(GenWeightProgress.StateEnum.STEADY);
    }
    @Test void exactTenthIsMovementAndSmallerChangesAreSteady() {
        assertThat(weekly("80", "79.9", "75").getState()).isEqualTo(GenWeightProgress.StateEnum.TOWARD);
        assertThat(weekly("80", "80.1", "75").getArrow()).isEqualTo(GenWeightProgress.ArrowEnum.UP);
        var steady = weekly("80", "79.91", "75");
        assertThat(steady.getState()).isEqualTo(GenWeightProgress.StateEnum.STEADY);
        assertThat(steady.getArrow()).isEqualTo(GenWeightProgress.ArrowEnum.STEADY);
    }
    @Test void sparseWindowsExcludeMissingDaysAndNeverIncludeFutureReadings() {
        var result = WeightProgressCalculator.calculate(List.of(reading(DAY.minusDays(13), "82"), reading(DAY.minusDays(7), "80"),
                reading(DAY.minusDays(6), "78"), reading(DAY, "76"), reading(DAY.plusDays(1), "99")), prefs("75", DAY), DAY, 7);
        assertThat(result.getCoverage()).isEqualTo(2);
        assertThat(result.getPreviousCoverage()).isEqualTo(2);
        assertThat(result.getTrendKg()).isEqualTo(77);
        assertThat(result.getPreviousTrendKg()).isEqualTo(81);
        assertThat(result.getLatestWeightKg()).isEqualTo(76);
        assertThat(result.getSeries()).hasSize(7);
        assertThat(result.getSeries().get(1).getReadingKg()).isNull();
    }
    @Test void latestDailyReadingWinsAcrossAliasesAndUnitsWithoutDuplicates() {
        var early = reading(DAY, "80");
        var late = reading(DAY, "176"); late.setMetricName("weight"); late.setUnits("lbs"); late.setTimestamp(DAY.atTime(20, 0));
        var duplicate = reading(DAY, "79.8"); duplicate.setMetricName("body_mass"); duplicate.setTimestamp(late.getTimestamp());
        var invalid = reading(DAY, "0"); invalid.setTimestamp(DAY.atTime(22, 0));
        var unsupported = reading(DAY, "100"); unsupported.setUnits("unknown");
        var negative = reading(DAY, "-2");
        var result = WeightProgressCalculator.calculate(List.of(duplicate, early, late, invalid, unsupported, negative), prefs("75", DAY), DAY, 7);
        assertThat(result.getCoverage()).isEqualTo(1);
        assertThat(result.getTrendKg()).isCloseTo(79.83225712, within(0.00000001));
    }
    @Test void oneDaySufficesAndOldLatestReadingIsRetainedWithoutInventingTrend() {
        var result = WeightProgressCalculator.calculate(List.of(reading(DAY.minusDays(8), "80")), prefs("75", DAY), DAY, 30);
        assertThat(result.getLatestWeightKg()).isEqualTo(80);
        assertThat(result.getLatestWeightDate()).isEqualTo(DAY.minusDays(8));
        assertThat(result.getTrendKg()).isNull();
        assertThat(result.getArrow()).isNull();
        assertThat(result.getState()).isEqualTo(GenWeightProgress.StateEnum.NO_DATA);
    }
    @Test void recordStartsWithFirstEligibleTrendUsingEarlierSmoothing() {
        var result = WeightProgressCalculator.calculate(List.of(reading(DAY.minusDays(2), "80"), reading(DAY, "78")), prefs("75", DAY), DAY, 30);
        assertThat(result.getBestGapKg()).isEqualTo(4);
        assertThat(result.getBestDate()).isEqualTo(DAY);
        assertThat(result.getState()).isNotEqualTo(GenWeightProgress.StateEnum.NEW_BEST);
    }
    @Test void recordsRequireTenthImprovementAndTiesKeepOriginalDate() {
        var start = DAY.minusDays(1);
        for (String today : List.of("79.8", "79.82", "80")) {
            var result = WeightProgressCalculator.calculate(List.of(reading(start, "80"), reading(DAY, today)), prefs("75", start), DAY, 30);
            assertThat(result.getState() == GenWeightProgress.StateEnum.NEW_BEST).isEqualTo(today.equals("79.8"));
            if (today.equals("80")) assertThat(result.getBestDate()).isEqualTo(start);
        }
    }
    @Test void recordsSpanWholeChallengeAndIgnoreCloserPreChallengeTrends() {
        var start = DAY.minusDays(100);
        var result = WeightProgressCalculator.calculate(List.of(reading(start.minusDays(20), "75"), reading(start, "77"), reading(DAY, "80")), prefs("75", start), DAY, 30);
        assertThat(result.getBestGapKg()).isEqualTo(2);
        assertThat(result.getBestDate()).isEqualTo(start);
        assertThat(result.getSeries()).hasSize(30);
        var historical = WeightProgressCalculator.calculate(List.of(reading(start.minusDays(20), "75")), prefs("75", start), start.minusDays(20), 7);
        assertThat(historical.getState()).isEqualTo(GenWeightProgress.StateEnum.BEFORE_CHALLENGE);
        assertThat(historical.getBestDate()).isNull();
        assertThat(historical.getBestGapKg()).isNull();
        assertThat(historical.getLatestWeightKg()).isEqualTo(75);
    }
    @Test void emptyDataAndCalendarBoundaries() {
        LocalDate leapDay = LocalDate.of(2024, 3, 1);
        var result = WeightProgressCalculator.calculate(List.of(reading(leapDay.minusDays(6), "80")), prefs("75", leapDay), leapDay, 7);
        assertThat(result.getCoverage()).isEqualTo(1);
        assertThat(result.getSeries()).extracting("date").contains(LocalDate.of(2024, 2, 29));
        var empty = WeightProgressCalculator.calculate(List.of(), prefs("75", DAY), DAY, 30);
        assertThat(empty.getLatestWeightKg()).isNull();
        assertThat(empty.getBestGapKg()).isNull();
        assertThat(empty.getCoverage()).isZero();
        assertThat(empty.getSeries()).allMatch(point -> point.getReadingKg() == null && point.getTrendKg() == null);
    }
}
