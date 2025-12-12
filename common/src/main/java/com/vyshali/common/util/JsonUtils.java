package com.vyshali.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSON utility methods with safe parsing.
 * Provides both Optional-returning and direct-returning methods.
 */
public final class JsonUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    private JsonUtils() {}

    // ═══════════════════════════════════════════════════════════════════════
    // DIRECT PARSING (returns T or null, throws on parse error)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Parse JSON string to object. Returns null if json is null/blank.
     * Throws RuntimeException on parse failure.
     * 
     * Usage: PositionUpdate update = JsonUtils.fromJson(json, PositionUpdate.class);
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON to {}: {}", clazz.getSimpleName(), e.getMessage());
            throw new RuntimeException("JSON parse error: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JSON string to parameterized type (e.g., List<Position>).
     * 
     * Usage: List<Position> positions = JsonUtils.fromJson(json, new TypeReference<List<Position>>() {});
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON to {}: {}", typeRef.getType(), e.getMessage());
            throw new RuntimeException("JSON parse error: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JSON to Map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON to Map: {}", e.getMessage());
            throw new RuntimeException("JSON parse error: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JSON to List of Maps.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> fromJsonToList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON to List: {}", e.getMessage());
            throw new RuntimeException("JSON parse error: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SAFE PARSING (returns Optional<T>, never throws)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Safely parse JSON string to object. Returns Optional.empty() on any error.
     * 
     * Usage: Optional<PositionUpdate> update = JsonUtils.parse(json, PositionUpdate.class);
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
     * Safely parse JSON string to parameterized type.
     */
    public static <T> Optional<T> parse(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(json, typeRef));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse JSON string to JsonNode for manual traversal.
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

    // ═══════════════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Convert object to JSON string. Returns null on error.
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convert object to JSON string (Optional version).
     */
    public static Optional<String> toJsonOpt(Object obj) {
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

    // ═══════════════════════════════════════════════════════════════════════
    // JSON NODE EXTRACTION HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Safe extraction of String from JsonNode.
     */
    public static String getString(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText(defaultValue);
    }

    /**
     * Safe extraction of String from JsonNode (returns null if missing).
     */
    public static String getString(JsonNode node, String field) {
        return getString(node, field, null);
    }

    /**
     * Safe extraction of int from JsonNode.
     */
    public static int getInt(JsonNode node, String field, int defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asInt(defaultValue);
    }

    /**
     * Safe extraction of long from JsonNode.
     */
    public static long getLong(JsonNode node, String field, long defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asLong(defaultValue);
    }

    /**
     * Safe extraction of double from JsonNode.
     */
    public static double getDouble(JsonNode node, String field, double defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asDouble(defaultValue);
    }

    /**
     * Safe extraction of BigDecimal from JsonNode.
     */
    public static BigDecimal getBigDecimal(JsonNode node, String field, BigDecimal defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        try {
            String text = node.get(field).asText();
            if (text == null || text.isBlank()) {
                return defaultValue;
            }
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Safe extraction of BigDecimal (returns ZERO if missing).
     */
    public static BigDecimal getBigDecimal(JsonNode node, String field) {
        return getBigDecimal(node, field, BigDecimal.ZERO);
    }

    /**
     * Safe extraction of boolean from JsonNode.
     */
    public static boolean getBoolean(JsonNode node, String field, boolean defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asBoolean(defaultValue);
    }

    /**
     * Check if field exists and is not null.
     */
    public static boolean hasField(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull();
    }

    /**
     * Check if node is an array.
     */
    public static boolean isArray(JsonNode node, String field) {
        return node != null && node.has(field) && node.get(field).isArray();
    }

    /**
     * Check if node is an object.
     */
    public static boolean isObject(JsonNode node, String field) {
        return node != null && node.has(field) && node.get(field).isObject();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OBJECT MAPPER ACCESS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get the shared ObjectMapper instance.
     * Use this when you need to configure additional modules or perform
     * complex serialization.
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * Convert object to JsonNode.
     */
    public static JsonNode toJsonNode(Object obj) {
        return MAPPER.valueToTree(obj);
    }

    /**
     * Convert JsonNode to object.
     */
    public static <T> T fromJsonNode(JsonNode node, Class<T> clazz) {
        try {
            return MAPPER.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JsonNode to {}: {}", clazz.getSimpleName(), e.getMessage());
            throw new RuntimeException("JSON conversion error: " + e.getMessage(), e);
        }
    }
}
