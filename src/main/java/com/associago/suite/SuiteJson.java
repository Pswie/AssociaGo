package com.associago.suite;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/** Strict wire types for suite DTOs, without changing the application's ObjectMapper. */
public final class SuiteJson {
    private SuiteJson() { }

    public static final class StrictInteger extends StdDeserializer<Integer> {
        public StrictInteger() {
            super(Integer.class);
        }

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                throw JsonMappingException.from(parser, "An integer JSON value is required.");
            }
            return parser.getIntValue();
        }
    }

    public static final class StrictLong extends StdDeserializer<Long> {
        public StrictLong() {
            super(Long.class);
        }

        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                throw JsonMappingException.from(parser, "An integer JSON value is required.");
            }
            return parser.getLongValue();
        }
    }

    public static final class StrictString extends StdDeserializer<String> {
        public StrictString() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                throw JsonMappingException.from(parser, "A JSON string is required for monetary amounts.");
            }
            return parser.getText();
        }
    }
}
