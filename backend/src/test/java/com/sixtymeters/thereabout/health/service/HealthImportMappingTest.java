package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class HealthImportMappingTest {
    private final HealthMetricMapper metrics = new HealthMetricMapper();
    private final WorkoutMapper workouts = new WorkoutMapper();

    @Test void retainsLocalTimestampForEverySupportedMetricShape() {
        var date = OffsetDateTime.parse("2026-03-29T23:45:00+02:00");
        for (var metric : new GenHealthMetricDataInner[]{
                new GenMetricDataQuantity().date(date),
                new GenMetricDataBloodPressure().date(date),
                new GenMetricDataHeartRate().date(date),
                new GenMetricDataBloodGlucose().date(date),
                new GenMetricDataSexualActivity().date(date),
                new GenMetricDataHandwashing().date(date),
                new GenMetricDataToothbrushing().date(date),
                new GenMetricDataInsulin().date(date),
                new GenMetricDataHeartRateNotification().start(date)}) {
            assertThat(metrics.extractTimestamp(metric)).isEqualTo(date.toLocalDateTime());
        }
        assertThat(metrics.extractTimestamp(new GenMetricDataSleep().date(LocalDate.parse("2026-03-29"))))
                .isEqualTo(LocalDate.parse("2026-03-29").atStartOfDay());
        assertThat(metrics.extractTimestamp(new GenMetricDataQuantity())).isNull();
    }

    @Test void preservesSparseSleepAndExactEnumValuesWithoutReflection() {
        assertThat(metrics.extractQty(new GenMetricDataSleep())).isNull();
        assertThat(metrics.extractQty(new GenMetricDataSleep().totalSleep(new BigDecimal("7.5"))))
                .isEqualByComparingTo("7.5");
        var mapped = workouts.mapWorkoutToEntity(new GenWorkout()
                .id("mapping-only").name("Swim")
                .location(GenWorkout.LocationEnum.OPEN_WATER)
                .strokeStyle(GenWorkout.StrokeStyleEnum.FREESTYLE)
                .salinity(GenWorkout.SalinityEnum.SALT_WATER));
        assertThat(mapped.getLocation()).isEqualTo("Open Water");
        assertThat(mapped.getStrokeStyle()).isEqualTo("Freestyle");
        assertThat(mapped.getSalinity()).isEqualTo("Salt Water");
        assertThat(workouts.mapWorkoutToEntity(new GenWorkout()).getLocation()).isNull();
    }
}
