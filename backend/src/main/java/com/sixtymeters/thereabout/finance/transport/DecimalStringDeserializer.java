package com.sixtymeters.thereabout.finance.transport;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reject numeric JSON tokens before rounding or coercion can lose financial precision. */
public class DecimalStringDeserializer extends ValueDeserializer<String> {
  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) {
    if (!parser.hasToken(JsonToken.VALUE_STRING)) {
      return context.reportInputMismatch(String.class, "Financial amounts must be decimal strings");
    }
    return parser.getString();
  }
}
