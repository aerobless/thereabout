package com.sixtymeters.thereabout.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.generated.model.*;

import java.io.IOException;

public class HealthMetricDataDeserializer extends JsonDeserializer<GenHealthMetricDataInner> {

    @Override
    public GenHealthMetricDataInner deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        if (node.hasNonNull("systolic") || node.hasNonNull("diastolic")) {
            return mapper.treeToValue(node, GenMetricDataBloodPressure.class);
        }
        if (node.hasNonNull("Min") || node.hasNonNull("Avg") || node.hasNonNull("Max")) {
            return mapper.treeToValue(node, GenMetricDataHeartRate.class);
        }
        if (node.hasNonNull("totalSleep") || node.hasNonNull("sleepStart") || node.hasNonNull("sleepEnd")) {
            return mapper.treeToValue(node, GenMetricDataSleep.class);
        }
        if (node.hasNonNull("mealTime")) {
            return mapper.treeToValue(node, GenMetricDataBloodGlucose.class);
        }
        if (node.hasNonNull("Unspecified") || node.hasNonNull("Protection Used") || node.hasNonNull("Protection Not Used")) {
            return mapper.treeToValue(node, GenMetricDataSexualActivity.class);
        }
        if (node.hasNonNull("reason")) {
            return mapper.treeToValue(node, GenMetricDataInsulin.class);
        }
        if (node.hasNonNull("start") && node.hasNonNull("end") && node.hasNonNull("threshold")) {
            return mapper.treeToValue(node, GenMetricDataHeartRateNotification.class);
        }
        if (node.hasNonNull("value")) {
            // Handwashing and toothbrushing have the same shape; default to handwashing.
            return mapper.treeToValue(node, GenMetricDataHandwashing.class);
        }

        return mapper.treeToValue(node, GenMetricDataQuantity.class);
    }
}
