package ru.itmo.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class PriorityDeserializer extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT
                || parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            try {
                return parser.getDecimalValue().intValueExact();
            } catch (ArithmeticException exception) {
                return context.reportInputMismatch(Integer.class, "Priority must be an integer");
            }
        }
        return context.reportInputMismatch(Integer.class, "Priority must be a JSON number");
    }
}
