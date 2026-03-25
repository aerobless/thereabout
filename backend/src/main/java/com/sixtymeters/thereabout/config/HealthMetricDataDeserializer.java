package com.sixtymeters.thereabout.config;

import com.sixtymeters.thereabout.generated.model.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class HealthMetricDataDeserializer extends ValueDeserializer<GenHealthMetricDataInner> {

    @Override
    public GenHealthMetricDataInner deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = ctxt.readTree(p);

        if (node.hasNonNull("systolic") || node.hasNonNull("diastolic")) {
            return ctxt.readTreeAsValue(node, GenMetricDataBloodPressure.class);
        }
        if (node.hasNonNull("Min") || node.hasNonNull("Avg") || node.hasNonNull("Max")) {
            return ctxt.readTreeAsValue(node, GenMetricDataHeartRate.class);
        }
        if (node.hasNonNull("totalSleep") || node.hasNonNull("sleepStart") || node.hasNonNull("sleepEnd")) {
            return ctxt.readTreeAsValue(node, GenMetricDataSleep.class);
        }
        if (node.hasNonNull("mealTime")) {
            return ctxt.readTreeAsValue(node, GenMetricDataBloodGlucose.class);
        }
        if (node.hasNonNull("Unspecified") || node.hasNonNull("Protection Used") || node.hasNonNull("Protection Not Used")) {
            return ctxt.readTreeAsValue(node, GenMetricDataSexualActivity.class);
        }
        if (node.hasNonNull("reason")) {
            return ctxt.readTreeAsValue(node, GenMetricDataInsulin.class);
        }
        if (node.hasNonNull("start") && node.hasNonNull("end") && node.hasNonNull("threshold")) {
            return ctxt.readTreeAsValue(node, GenMetricDataHeartRateNotification.class);
        }
        if (node.hasNonNull("value")) {
            // Handwashing and toothbrushing have the same shape; default to handwashing.
            return ctxt.readTreeAsValue(node, GenMetricDataHandwashing.class);
        }

        return ctxt.readTreeAsValue(node, GenMetricDataQuantity.class);
    }
}
