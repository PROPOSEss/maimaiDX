package com.maimai.maidx.assistant.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolSchemas {

    private ToolSchemas() {
    }

    public static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(properties));
        schema.put("required", List.of(required));
        schema.put("additionalProperties", false);
        return schema;
    }

    public static Map<String, Object> integer(int minimum, int maximum, String description) {
        return Map.of(
                "type", "integer",
                "minimum", minimum,
                "maximum", maximum,
                "description", description);
    }

    public static Map<String, Object> number(double minimum, double maximum, String description) {
        return Map.of(
                "type", "number",
                "minimum", minimum,
                "maximum", maximum,
                "description", description);
    }

    public static Map<String, Object> string(int minLength, int maxLength, String description) {
        return Map.of(
                "type", "string",
                "minLength", minLength,
                "maxLength", maxLength,
                "nonBlank", true,
                "description", description);
    }
}
