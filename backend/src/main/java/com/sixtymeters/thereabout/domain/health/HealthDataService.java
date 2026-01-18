package com.sixtymeters.thereabout.domain.health;

import com.sixtymeters.thereabout.generated.model.*;
import com.sixtymeters.thereabout.model.health.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthDataService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

    private final HealthMetricRepository healthMetricRepository;
    private final HealthMetricBloodPressureRepository bloodPressureRepository;
    private final HealthMetricHeartRateRepository heartRateRepository;
    private final HealthMetricSleepRepository sleepRepository;
    private final HealthMetricBloodGlucoseRepository bloodGlucoseRepository;
    private final HealthMetricSexualActivityRepository sexualActivityRepository;
    private final HealthMetricHandwashingRepository handwashingRepository;
    private final HealthMetricToothbrushingRepository toothbrushingRepository;
    private final HealthMetricInsulinRepository insulinRepository;
    private final HealthMetricHeartRateNotificationRepository heartRateNotificationRepository;
    private final HealthMetricHeartRateNotificationDataRepository heartRateNotificationDataRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutTimeSeriesDataRepository workoutTimeSeriesDataRepository;

    @Transactional
    public void saveHealthMetrics(List<GenHealthMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }

        for (GenHealthMetric metric : metrics) {
            if (metric.getName() == null || metric.getData() == null || metric.getData().isEmpty()) {
                log.warn("Skipping metric with null name or empty data: {}", metric);
                continue;
            }

            String metricName = metric.getName();
            String units = metric.getUnits();

            // Group data by date for efficient upsert
            Map<LocalDate, List<GenHealthMetricDataInner>> dataByDate = groupDataByDate(metric.getData());

            for (Map.Entry<LocalDate, List<GenHealthMetricDataInner>> entry : dataByDate.entrySet()) {
                LocalDate metricDate = entry.getKey();
                List<GenHealthMetricDataInner> dataItems = entry.getValue();

                // Delete existing records for this metric name and date
                healthMetricRepository.deleteByMetricNameAndMetricDate(metricName, metricDate);

                // Insert new records
                for (GenHealthMetricDataInner dataItem : dataItems) {
                    saveMetricDataItem(metricName, metricDate, units, dataItem);
                }
            }
        }
    }

    @Transactional
    public void saveWorkouts(List<GenWorkout> workouts) {
        if (workouts == null || workouts.isEmpty()) {
            return;
        }

        for (GenWorkout workout : workouts) {
            if (workout.getId() == null) {
                log.warn("Skipping workout with null id: {}", workout);
                continue;
            }

            WorkoutEntity workoutEntity = mapWorkoutToEntity(workout);
            workoutRepository.save(workoutEntity);

            // Delete existing time-series data and recreate
            workoutTimeSeriesDataRepository.deleteByWorkoutId(workout.getId());
            saveWorkoutTimeSeriesData(workout.getId(), workout);
        }
    }

    private Map<LocalDate, List<GenHealthMetricDataInner>> groupDataByDate(List<GenHealthMetricDataInner> data) {
        return data.stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> {
                    LocalDateTime timestamp = extractTimestamp(item);
                    return timestamp != null ? timestamp.toLocalDate() : LocalDate.now();
                }));
    }

    private void saveMetricDataItem(String metricName, LocalDate metricDate, String units, GenHealthMetricDataInner dataItem) {
        LocalDateTime timestamp = extractTimestamp(dataItem);
        String source = extractSource(dataItem);

        // Create base health metric entity
        HealthMetricEntity baseEntity = HealthMetricEntity.builder()
                .metricName(metricName)
                .metricDate(metricDate)
                .timestamp(timestamp)
                .units(units)
                .qty(extractQty(dataItem))
                .source(source)
                .build();

        baseEntity = healthMetricRepository.save(baseEntity);

        // Determine metric type and create detail entity
        if (isBloodPressure(metricName, dataItem)) {
            saveBloodPressure(baseEntity, dataItem);
        } else if (isHeartRate(metricName, dataItem)) {
            saveHeartRate(baseEntity, dataItem);
        } else if (isSleepAnalysis(metricName, dataItem)) {
            saveSleep(baseEntity, dataItem);
        } else if (isBloodGlucose(metricName, dataItem)) {
            saveBloodGlucose(baseEntity, dataItem);
        } else if (isSexualActivity(metricName, dataItem)) {
            saveSexualActivity(baseEntity, dataItem);
        } else if (isHandwashing(metricName, dataItem)) {
            saveHandwashing(baseEntity, dataItem);
        } else if (isToothbrushing(metricName, dataItem)) {
            saveToothbrushing(baseEntity, dataItem);
        } else if (isInsulin(metricName, dataItem)) {
            saveInsulin(baseEntity, dataItem);
        } else if (isHeartRateNotification(dataItem)) {
            saveHeartRateNotification(baseEntity, dataItem);
        }
        // Simple quantity metrics are already saved in base entity
    }

    private boolean isBloodPressure(String metricName, GenHealthMetricDataInner dataItem) {
        return "blood_pressure".equals(metricName) && dataItem instanceof GenMetricDataBloodPressure;
    }

    private boolean isHeartRate(String metricName, GenHealthMetricDataInner dataItem) {
        return "heart_rate".equals(metricName) && dataItem instanceof GenMetricDataHeartRate;
    }

    private boolean isSleepAnalysis(String metricName, GenHealthMetricDataInner dataItem) {
        return "sleep_analysis".equals(metricName) && dataItem instanceof GenMetricDataSleep;
    }

    private boolean isBloodGlucose(String metricName, GenHealthMetricDataInner dataItem) {
        return metricName != null && metricName.toLowerCase().contains("glucose") && dataItem instanceof GenMetricDataBloodGlucose;
    }

    private boolean isSexualActivity(String metricName, GenHealthMetricDataInner dataItem) {
        return metricName != null && metricName.toLowerCase().contains("sexual") && dataItem instanceof GenMetricDataSexualActivity;
    }

    private boolean isHandwashing(String metricName, GenHealthMetricDataInner dataItem) {
        return "handwashing".equals(metricName);
    }

    private boolean isToothbrushing(String metricName, GenHealthMetricDataInner dataItem) {
        return "toothbrushing".equals(metricName);
    }

    private boolean isInsulin(String metricName, GenHealthMetricDataInner dataItem) {
        return metricName != null && metricName.toLowerCase().contains("insulin") && dataItem instanceof GenMetricDataInsulin;
    }

    private boolean isHeartRateNotification(GenHealthMetricDataInner dataItem) {
        return dataItem instanceof GenMetricDataHeartRateNotification;
    }

    private LocalDateTime extractTimestamp(Object dataItem) {
        if (dataItem instanceof GenMetricDataQuantity) {
            OffsetDateTime date = ((GenMetricDataQuantity) dataItem).getDate();
            return date != null ? date.toLocalDateTime() : null;
        } else if (dataItem instanceof GenMetricDataBloodPressure) {
            OffsetDateTime date = ((GenMetricDataBloodPressure) dataItem).getDate();
            return date != null ? date.toLocalDateTime() : null;
        } else if (dataItem instanceof GenMetricDataHeartRate) {
            OffsetDateTime date = ((GenMetricDataHeartRate) dataItem).getDate();
            return date != null ? date.toLocalDateTime() : null;
        } else if (dataItem instanceof GenMetricDataSleep) {
            LocalDate date = ((GenMetricDataSleep) dataItem).getDate();
            return date != null ? date.atStartOfDay() : null;
        } else if (dataItem instanceof GenMetricDataBloodGlucose) {
            OffsetDateTime date = ((GenMetricDataBloodGlucose) dataItem).getDate();
            return date != null ? date.toLocalDateTime() : null;
        } else if (dataItem instanceof GenMetricDataSexualActivity) {
            OffsetDateTime date = ((GenMetricDataSexualActivity) dataItem).getDate();
            return date != null ? date.toLocalDateTime() : null;
        } else if (dataItem instanceof GenMetricDataHandwashing) {
            OffsetDateTime date = ((GenMetricDataHandwashing) dataItem).getDate();
            return date != null ? date.toLocalDateTime() : null;
        } else if (dataItem instanceof GenMetricDataToothbrushing) {
            OffsetDateTime date = ((GenMetricDataToothbrushing) dataItem).getDate();
            return date != null ? date.toLocalDateTime() : null;
        } else if (dataItem instanceof GenMetricDataInsulin) {
            OffsetDateTime date = ((GenMetricDataInsulin) dataItem).getDate();
            return date != null ? date.toLocalDateTime() : null;
        } else if (dataItem instanceof GenMetricDataHeartRateNotification) {
            OffsetDateTime start = ((GenMetricDataHeartRateNotification) dataItem).getStart();
            return start != null ? start.toLocalDateTime() : null;
        }
        
        // Fallback to reflection-based extraction
        String dateStr = extractFieldAsString(dataItem, "date");
        if (dateStr == null) {
            return null;
        }
        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr, DATE_TIME_FORMATTER);
            return zonedDateTime.toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr, e);
            return null;
        }
    }

    private String extractSource(GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataQuantity) {
            return ((GenMetricDataQuantity) dataItem).getSource();
        }
        return null;
    }

    private BigDecimal extractQty(GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataQuantity) {
            Number qty = ((GenMetricDataQuantity) dataItem).getQty();
            return qty != null ? BigDecimal.valueOf(qty.doubleValue()) : null;
        } else if (dataItem instanceof GenMetricDataBloodGlucose) {
            Number qty = ((GenMetricDataBloodGlucose) dataItem).getQty();
            return qty != null ? BigDecimal.valueOf(qty.doubleValue()) : null;
        } else if (dataItem instanceof GenMetricDataHandwashing) {
            Number qty = ((GenMetricDataHandwashing) dataItem).getQty();
            return qty != null ? BigDecimal.valueOf(qty.doubleValue()) : null;
        } else if (dataItem instanceof GenMetricDataToothbrushing) {
            Number qty = ((GenMetricDataToothbrushing) dataItem).getQty();
            return qty != null ? BigDecimal.valueOf(qty.doubleValue()) : null;
        } else if (dataItem instanceof GenMetricDataInsulin) {
            Number qty = ((GenMetricDataInsulin) dataItem).getQty();
            return qty != null ? BigDecimal.valueOf(qty.doubleValue()) : null;
        }
        return null;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private void saveBloodPressure(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataBloodPressure) {
            GenMetricDataBloodPressure bp = (GenMetricDataBloodPressure) dataItem;
            if (bp.getSystolic() != null && bp.getDiastolic() != null) {
                HealthMetricBloodPressureEntity entity = HealthMetricBloodPressureEntity.builder()
                        .healthMetric(baseEntity)
                        .systolic(BigDecimal.valueOf(bp.getSystolic().doubleValue()))
                        .diastolic(BigDecimal.valueOf(bp.getDiastolic().doubleValue()))
                        .build();
                bloodPressureRepository.save(entity);
            }
        }
    }

    private void saveHeartRate(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataHeartRate) {
            GenMetricDataHeartRate hr = (GenMetricDataHeartRate) dataItem;
            if (hr.getMin() != null && hr.getAvg() != null && hr.getMax() != null) {
                HealthMetricHeartRateEntity entity = HealthMetricHeartRateEntity.builder()
                        .healthMetric(baseEntity)
                        .minValue(BigDecimal.valueOf(hr.getMin().doubleValue()))
                        .avgValue(BigDecimal.valueOf(hr.getAvg().doubleValue()))
                        .maxValue(BigDecimal.valueOf(hr.getMax().doubleValue()))
                        .build();
                heartRateRepository.save(entity);
            }
        }
    }

    private void saveSleep(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataSleep) {
            GenMetricDataSleep sleep = (GenMetricDataSleep) dataItem;
            HealthMetricSleepEntity entity = HealthMetricSleepEntity.builder()
                    .healthMetric(baseEntity)
                    .totalSleep(toBigDecimal(sleep.getTotalSleep()))
                    .asleep(toBigDecimal(sleep.getAsleep()))
                    .awake(toBigDecimal(sleep.getAwake()))
                    .core(toBigDecimal(sleep.getCore()))
                    .deep(toBigDecimal(sleep.getDeep()))
                    .rem(toBigDecimal(sleep.getRem()))
                    .sleepStart(parseOffsetDateTime(sleep.getSleepStart()))
                    .sleepEnd(parseOffsetDateTime(sleep.getSleepEnd()))
                    .inBed(toBigDecimal(sleep.getInBed()))
                    .inBedStart(parseOffsetDateTime(sleep.getInBedStart()))
                    .inBedEnd(parseOffsetDateTime(sleep.getInBedEnd()))
                    .build();
            sleepRepository.save(entity);
        }
    }

    private void saveBloodGlucose(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataBloodGlucose) {
            GenMetricDataBloodGlucose bg = (GenMetricDataBloodGlucose) dataItem;
            HealthMetricBloodGlucoseEntity entity = HealthMetricBloodGlucoseEntity.builder()
                    .healthMetric(baseEntity)
                    .mealTime(bg.getMealTime() != null ? bg.getMealTime().getValue() : null)
                    .build();
            bloodGlucoseRepository.save(entity);
        }
    }

    private void saveSexualActivity(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataSexualActivity) {
            GenMetricDataSexualActivity sa = (GenMetricDataSexualActivity) dataItem;
            HealthMetricSexualActivityEntity entity = HealthMetricSexualActivityEntity.builder()
                    .healthMetric(baseEntity)
                    .unspecified(sa.getUnspecified() != null ? sa.getUnspecified().intValue() : null)
                    .protectionUsed(sa.getProtectionUsed() != null ? sa.getProtectionUsed().intValue() : null)
                    .protectionNotUsed(sa.getProtectionNotUsed() != null ? sa.getProtectionNotUsed().intValue() : null)
                    .build();
            sexualActivityRepository.save(entity);
        }
    }

    private void saveHandwashing(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        String value = extractHandOrToothValue(dataItem);
        if (value != null) {
            HealthMetricHandwashingEntity entity = HealthMetricHandwashingEntity.builder()
                    .healthMetric(baseEntity)
                    .value(value)
                    .build();
            handwashingRepository.save(entity);
        }
    }

    private void saveToothbrushing(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        String value = extractHandOrToothValue(dataItem);
        if (value != null) {
            HealthMetricToothbrushingEntity entity = HealthMetricToothbrushingEntity.builder()
                    .healthMetric(baseEntity)
                    .value(value)
                    .build();
            toothbrushingRepository.save(entity);
        }
    }

    private void saveInsulin(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataInsulin) {
            GenMetricDataInsulin insulin = (GenMetricDataInsulin) dataItem;
            HealthMetricInsulinEntity entity = HealthMetricInsulinEntity.builder()
                    .healthMetric(baseEntity)
                    .reason(insulin.getReason() != null ? insulin.getReason().getValue() : null)
                    .build();
            insulinRepository.save(entity);
        }
    }

    private void saveHeartRateNotification(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataHeartRateNotification) {
            GenMetricDataHeartRateNotification hrn = (GenMetricDataHeartRateNotification) dataItem;
            OffsetDateTime start = hrn.getStart();
            OffsetDateTime end = hrn.getEnd();
            BigDecimal threshold = toBigDecimal(hrn.getThreshold());

            if (start != null && end != null && threshold != null) {
                HealthMetricHeartRateNotificationEntity entity = HealthMetricHeartRateNotificationEntity.builder()
                        .healthMetric(baseEntity)
                        .start(start.toLocalDateTime())
                        .end(end.toLocalDateTime())
                        .threshold(threshold)
                        .build();
                entity = heartRateNotificationRepository.save(entity);

                // Save heart rate data array
                if (hrn.getHeartRate() != null) {
                    for (GenMetricDataHeartRateNotificationHeartRateInner hrItem : hrn.getHeartRate()) {
                        saveHeartRateNotificationData(entity, hrItem);
                    }
                }
            }
        }
    }

    private void saveHeartRateNotificationData(HealthMetricHeartRateNotificationEntity parent, GenMetricDataHeartRateNotificationHeartRateInner dataItem) {
        if (dataItem == null || dataItem.getHr() == null) {
            return;
        }

        LocalDateTime timestampStart = null;
        LocalDateTime timestampEnd = null;
        BigDecimal intervalDuration = null;
        String intervalUnits = null;

        if (dataItem.getTimestamp() != null) {
            GenMetricDataHeartRateNotificationHeartRateInnerTimestamp timestamp = dataItem.getTimestamp();
            timestampStart = parseOffsetDateTime(timestamp.getStart());
            timestampEnd = parseOffsetDateTime(timestamp.getEnd());
            if (timestamp.getInterval() != null) {
                GenMetricDataHeartRateNotificationHeartRateInnerTimestampInterval interval = timestamp.getInterval();
                intervalDuration = toBigDecimal(interval.getDuration());
                intervalUnits = interval.getUnits();
            }
        }

        HealthMetricHeartRateNotificationDataEntity entity = HealthMetricHeartRateNotificationDataEntity.builder()
                .healthMetricHeartRateNotification(parent)
                .hr(dataItem.getHr())
                .units(dataItem.getUnits())
                .timestampStart(timestampStart)
                .timestampEnd(timestampEnd)
                .intervalDuration(intervalDuration)
                .intervalUnits(intervalUnits)
                .build();
        heartRateNotificationDataRepository.save(entity);
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) {
            return null;
        }
        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
            return zonedDateTime.toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse date-time: {}", dateTimeStr, e);
            return null;
        }
    }

    private LocalDateTime parseOffsetDateTime(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.toLocalDateTime() : null;
    }

    private String extractHandOrToothValue(GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataHandwashing) {
            GenMetricDataHandwashing hw = (GenMetricDataHandwashing) dataItem;
            return hw.getValue() != null ? hw.getValue().getValue() : null;
        }
        if (dataItem instanceof GenMetricDataToothbrushing) {
            GenMetricDataToothbrushing tb = (GenMetricDataToothbrushing) dataItem;
            return tb.getValue() != null ? tb.getValue().getValue() : null;
        }
        return null;
    }

    private Object extractField(Object obj, String fieldName) {
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).get(fieldName);
        }
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod("get" + capitalize(fieldName));
            return method.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractFieldAsString(Object obj, String fieldName) {
        Object value = extractField(obj, fieldName);
        return value != null ? value.toString() : null;
    }

    private WorkoutEntity mapWorkoutToEntity(GenWorkout workout) {
        OffsetDateTime start = workout.getStart();
        OffsetDateTime end = workout.getEnd();
        
        WorkoutEntity.WorkoutEntityBuilder builder = WorkoutEntity.builder()
                .id(workout.getId())
                .name(workout.getName())
                .start(start != null ? start.toLocalDateTime() : null)
                .end(end != null ? end.toLocalDateTime() : null)
                .duration(workout.getDuration() != null ? workout.getDuration().intValue() : null)
                .location(extractLocationValue(workout.getLocation()));

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

        builder.strokeStyle(extractEnumValue(workout.getStrokeStyle()))
                .swolfScore(workout.getSwolfScore() != null ? workout.getSwolfScore().intValue() : null)
                .salinity(extractEnumValue(workout.getSalinity()));

        return builder.build();
    }

    private void saveWorkoutTimeSeriesData(String workoutId, GenWorkout workout) {
        WorkoutEntity workoutEntity = workoutRepository.findById(workoutId).orElse(null);
        if (workoutEntity == null) {
            return;
        }

        List<WorkoutTimeSeriesDataEntity> timeSeriesData = new ArrayList<>();

        // Process all time-series arrays
        addTimeSeriesData(workoutEntity, workout.getActiveEnergy(), "activeEnergy", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getBasalEnergy(), "basalEnergy", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getCyclingCadence(), "cyclingCadence", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getCyclingDistance(), "cyclingDistance", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getCyclingPower(), "cyclingPower", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getCyclingSpeed(), "cyclingSpeed", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getSwimDistance(), "swimDistance", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getSwimStroke(), "swimStroke", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getStepCount(), "stepCount", timeSeriesData);
        addTimeSeriesData(workoutEntity, workout.getWalkingAndRunningDistance(), "walkingAndRunningDistance", timeSeriesData);

        // Process heart rate data (has Min/Avg/Max structure)
        if (workout.getHeartRateData() != null) {
            for (GenWorkoutHeartRateData hrData : workout.getHeartRateData()) {
                OffsetDateTime date = hrData.getDate();
                WorkoutTimeSeriesDataEntity entity = WorkoutTimeSeriesDataEntity.builder()
                        .workout(workoutEntity)
                        .dataType("heartRateData")
                        .timestamp(date != null ? date.toLocalDateTime() : null)
                        .minValue(toBigDecimal(hrData.getMin()))
                        .avgValue(toBigDecimal(hrData.getAvg()))
                        .maxValue(toBigDecimal(hrData.getMax()))
                        .units(hrData.getUnits())
                        .build();
                timeSeriesData.add(entity);
            }
        }

        // Process heart rate recovery
        if (workout.getHeartRateRecovery() != null) {
            for (GenWorkoutHeartRateData hrData : workout.getHeartRateRecovery()) {
                OffsetDateTime date = hrData.getDate();
                WorkoutTimeSeriesDataEntity entity = WorkoutTimeSeriesDataEntity.builder()
                        .workout(workoutEntity)
                        .dataType("heartRateRecovery")
                        .timestamp(date != null ? date.toLocalDateTime() : null)
                        .minValue(toBigDecimal(hrData.getMin()))
                        .avgValue(toBigDecimal(hrData.getAvg()))
                        .maxValue(toBigDecimal(hrData.getMax()))
                        .units(hrData.getUnits())
                        .build();
                timeSeriesData.add(entity);
            }
        }

        if (!timeSeriesData.isEmpty()) {
            workoutTimeSeriesDataRepository.saveAll(timeSeriesData);
        }
    }

    private void addTimeSeriesData(WorkoutEntity workoutEntity, List<GenQuantityData> dataList, String dataType, List<WorkoutTimeSeriesDataEntity> result) {
        if (dataList == null) {
            return;
        }
        for (GenQuantityData data : dataList) {
            OffsetDateTime date = data.getDate();
            WorkoutTimeSeriesDataEntity entity = WorkoutTimeSeriesDataEntity.builder()
                    .workout(workoutEntity)
                    .dataType(dataType)
                    .timestamp(date != null ? date.toLocalDateTime() : null)
                    .qty(toBigDecimal(data.getQty()))
                    .units(data.getUnits())
                    .source(data.getSource())
                    .build();
            result.add(entity);
        }
    }

    private BigDecimal toBigDecimal(Number number) {
        if (number == null) {
            return null;
        }
        return BigDecimal.valueOf(number.doubleValue());
    }

    private String extractLocationValue(Object location) {
        if (location == null) {
            return null;
        }
        if (location instanceof String) {
            return (String) location;
        }
        try {
            java.lang.reflect.Method method = location.getClass().getMethod("getValue");
            return (String) method.invoke(location);
        } catch (Exception e) {
            return location.toString();
        }
    }

    private String extractEnumValue(Object enumValue) {
        if (enumValue == null) {
            return null;
        }
        if (enumValue instanceof String) {
            return (String) enumValue;
        }
        try {
            java.lang.reflect.Method method = enumValue.getClass().getMethod("getValue");
            return (String) method.invoke(enumValue);
        } catch (Exception e) {
            return enumValue.toString();
        }
    }

    public HealthDataResponse getHealthData(LocalDate fromDate, LocalDate toDate) {
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
        List<HealthMetricEntity> metrics = healthMetricRepository.findByMetricDateBetween(fromDate, toDate);

        // Group metrics by name and convert to DailyMetricValue
        Map<String, List<DailyMetricValue>> metricsMap = metrics.stream()
                .collect(Collectors.groupingBy(
                        HealthMetricEntity::getMetricName,
                        Collectors.mapping(
                                entity -> DailyMetricValue.builder()
                                        .date(entity.getMetricDate())
                                        .qty(entity.getQty())
                                        .units(entity.getUnits())
                                        .timestamp(entity.getTimestamp())
                                        .source(entity.getSource())
                                        .build(),
                                Collectors.toList()
                        )
                ));

        // Retrieve workouts for the date range (convert LocalDate to LocalDateTime range)
        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(23, 59, 59, 999999999);
        List<WorkoutEntity> workouts = workoutRepository.findByStartBetween(fromDateTime, toDateTime);

        // Convert workouts to WorkoutSummary
        List<WorkoutSummary> workoutSummaries = workouts.stream()
                .map(workout -> WorkoutSummary.builder()
                        .id(workout.getId())
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
