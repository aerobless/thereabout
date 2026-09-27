package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.*;
import com.sixtymeters.thereabout.health.data.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthMetricImportService {
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
    private final HealthMetricMapper mapper;

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

    private Map<LocalDate, List<GenHealthMetricDataInner>> groupDataByDate(List<GenHealthMetricDataInner> data) {
        return data.stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> {
                    LocalDateTime timestamp = mapper.extractTimestamp(item);
                    return timestamp != null ? timestamp.toLocalDate() : LocalDate.now();
                }));
    }

    private void saveMetricDataItem(String metricName, LocalDate metricDate, String units, GenHealthMetricDataInner dataItem) {
        LocalDateTime timestamp = mapper.extractTimestamp(dataItem);
        String source = mapper.extractSource(dataItem);

        // Create base health metric entity
        HealthMetricEntity baseEntity = HealthMetricEntity.builder()
                .metricName(metricName)
                .metricDate(metricDate)
                .timestamp(timestamp)
                .units(units)
                .qty(mapper.extractQty(dataItem))
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
        String value = mapper.extractHandOrToothValue(dataItem);
        if (value != null) {
            HealthMetricHandwashingEntity entity = HealthMetricHandwashingEntity.builder()
                    .healthMetric(baseEntity)
                    .value(value)
                    .build();
            handwashingRepository.save(entity);
        }
    }

    private void saveToothbrushing(HealthMetricEntity baseEntity, GenHealthMetricDataInner dataItem) {
        String value = mapper.extractHandOrToothValue(dataItem);
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

    private LocalDateTime parseOffsetDateTime(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.toLocalDateTime() : null;
    }

    private BigDecimal toBigDecimal(Number number) {
        return number == null ? null : BigDecimal.valueOf(number.doubleValue());
    }
}
