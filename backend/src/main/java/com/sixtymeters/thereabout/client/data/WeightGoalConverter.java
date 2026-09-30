package com.sixtymeters.thereabout.client.data;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/** Keep the full precision accepted by the previous configuration value. */
@Converter
public class WeightGoalConverter implements AttributeConverter<BigDecimal,String> {
    public String convertToDatabaseColumn(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    public BigDecimal convertToEntityAttribute(String value) { return value == null ? null : new BigDecimal(value); }
}
