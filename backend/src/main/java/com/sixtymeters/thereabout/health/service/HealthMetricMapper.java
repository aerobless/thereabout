package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.*;
import com.sixtymeters.thereabout.health.data.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Component
public class HealthMetricMapper {

    LocalDateTime extractTimestamp(GenHealthMetricDataInner dataItem) {
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
        
        return null;
    }

    String extractSource(GenHealthMetricDataInner dataItem) {
        if (dataItem instanceof GenMetricDataQuantity) {
            return ((GenMetricDataQuantity) dataItem).getSource();
        }
        return null;
    }

    BigDecimal extractQty(GenHealthMetricDataInner dataItem) {
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
        } else if (dataItem instanceof GenMetricDataSleep) {
            Number total = ((GenMetricDataSleep) dataItem).getTotalSleep();
            return total != null ? BigDecimal.valueOf(total.doubleValue()) : null;
        }
        return null;
    }

    String extractHandOrToothValue(GenHealthMetricDataInner dataItem) {
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

}
