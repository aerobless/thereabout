package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.*;
import com.sixtymeters.thereabout.health.data.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
public class WorkoutMapper {

    WorkoutEntity mapWorkoutToEntity(GenWorkout workout) {
        OffsetDateTime start = workout.getStart();
        OffsetDateTime end = workout.getEnd();
        
        WorkoutEntity.WorkoutEntityBuilder builder = WorkoutEntity.builder()
                .sourceId(workout.getId())
                .name(workout.getName())
                .start(start != null ? start.toLocalDateTime() : null)
                .end(end != null ? end.toLocalDateTime() : null)
                .duration(workout.getDuration() != null ? workout.getDuration().intValue() : null)
                .location(workout.getLocation() == null ? null : workout.getLocation().getValue());

        if (workout.getActiveEnergyBurned() != null) {
            builder.activeEnergyBurnedQty(toBigDecimal(workout.getActiveEnergyBurned().getQty()))
                    .activeEnergyBurnedUnits(workout.getActiveEnergyBurned().getUnits());
        }

        if (workout.getIntensity() != null) {
            builder.intensityQty(toBigDecimal(workout.getIntensity().getQty()))
                    .intensityUnits(workout.getIntensity().getUnits());
        }

        if (workout.getDistance() != null) {
            builder.distanceQty(toBigDecimal(workout.getDistance().getQty()))
                    .distanceUnits(workout.getDistance().getUnits());
        }

        if (workout.getTemperature() != null) {
            builder.temperatureQty(toBigDecimal(workout.getTemperature().getQty()))
                    .temperatureUnits(workout.getTemperature().getUnits());
        }

        if (workout.getHumidity() != null) {
            builder.humidityQty(toBigDecimal(workout.getHumidity().getQty()))
                    .humidityUnits(workout.getHumidity().getUnits());
        }

        if (workout.getAvgSpeed() != null) {
            builder.avgSpeedQty(toBigDecimal(workout.getAvgSpeed().getQty()))
                    .avgSpeedUnits(workout.getAvgSpeed().getUnits());
        }

        if (workout.getMaxSpeed() != null) {
            builder.maxSpeedQty(toBigDecimal(workout.getMaxSpeed().getQty()))
                    .maxSpeedUnits(workout.getMaxSpeed().getUnits());
        }

        if (workout.getElevationUp() != null) {
            builder.elevationUpQty(toBigDecimal(workout.getElevationUp().getQty()))
                    .elevationUpUnits(workout.getElevationUp().getUnits());
        }

        if (workout.getElevationDown() != null) {
            builder.elevationDownQty(toBigDecimal(workout.getElevationDown().getQty()))
                    .elevationDownUnits(workout.getElevationDown().getUnits());
        }

        if (workout.getLapLength() != null) {
            builder.lapLengthQty(toBigDecimal(workout.getLapLength().getQty()))
                    .lapLengthUnits(workout.getLapLength().getUnits());
        }

        builder.strokeStyle(workout.getStrokeStyle() == null ? null : workout.getStrokeStyle().getValue())
                .swolfScore(workout.getSwolfScore() != null ? workout.getSwolfScore().intValue() : null)
                .salinity(workout.getSalinity() == null ? null : workout.getSalinity().getValue());

        return builder.build();
    }

    private BigDecimal toBigDecimal(Number number) {
        if (number == null) {
            return null;
        }
        return BigDecimal.valueOf(number.doubleValue());
    }

}
