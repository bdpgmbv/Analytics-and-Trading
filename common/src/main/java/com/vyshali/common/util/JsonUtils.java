package com.vyshali.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * JSON utility methods with safe parsing.
 */
public final class JsonUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {}

    /**
     * Parse JSON string to object.
     */
    public static <T> Optional<T> parse(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(json, clazz));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse JSON string to JsonNode.
     */
    public static Optional<JsonNode> parseTree(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON tree: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Convert object to JSON string.
     */
    public static Optional<String> toJson(Object obj) {
        if (obj == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.writeValueAsString(obj));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Convert object to pretty JSON string.
     */
    public static Optional<String> toPrettyJson(Object obj) {
        if (obj == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to pretty JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Safe extraction from JsonNode.
     */
    public static String getString(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText(defaultValue);
    }

    public static int getInt(JsonNode node, String field, int defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asInt(defaultValue);
    }

    public static long getLong(JsonNode node, String field, long defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asLong(defaultValue);
    }

    public static BigDecimal getBigDecimal(JsonNode node, String field, BigDecimal defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(node.get(field).asText());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(JsonNode node, String field, boolean defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asBoolean(defaultValue);
    }

    /**
     * Get the shared ObjectMapper instance.
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
