package com.maimai.maidx.assistant.tool;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ToolSchemaValidator {

    public void validate(AssistantToolDefinition definition, Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        Map<String, Object> schema = definition.parameterSchema();
        Map<String, Object> properties = asMap(schema.get("properties"), "properties");
        Set<String> required = asCollection(schema.get("required")).stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());

        for (String field : required) {
            if (!safeParams.containsKey(field) || safeParams.get(field) == null) {
                throw new IllegalArgumentException("tool参数缺失: " + field);
            }
        }
        if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            for (String field : safeParams.keySet()) {
                if (!properties.containsKey(field)) {
                    throw new IllegalArgumentException("不支持的tool参数: " + field);
                }
            }
        }
        for (Map.Entry<String, Object> entry : safeParams.entrySet()) {
            if (entry.getValue() != null) {
                validateValue(entry.getKey(), entry.getValue(), asMap(properties.get(entry.getKey()), entry.getKey()));
            }
        }
    }

    private void validateValue(String field, Object value, Map<String, Object> property) {
        String type = String.valueOf(property.get("type"));
        switch (type) {
            case "integer" -> {
                if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
                    throw new IllegalArgumentException(field + " 必须是整数");
                }
                validateRange(field, new BigDecimal(value.toString()), property);
            }
            case "number" -> {
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException(field + " 必须是数字");
                }
                validateRange(field, new BigDecimal(value.toString()), property);
            }
            case "string" -> {
                if (!(value instanceof String text)) {
                    throw new IllegalArgumentException(field + " 必须是字符串");
                }
                int minLength = intValue(property.get("minLength"), 0);
                int maxLength = intValue(property.get("maxLength"), Integer.MAX_VALUE);
                if (text.length() < minLength || text.length() > maxLength
                        || (Boolean.TRUE.equals(property.get("nonBlank")) && text.isBlank())) {
                    throw new IllegalArgumentException(field + " 长度或内容不合法");
                }
            }
            default -> throw new IllegalArgumentException("不支持的参数Schema类型: " + type);
        }
    }

    private void validateRange(String field, BigDecimal value, Map<String, Object> property) {
        if (property.get("minimum") != null
                && value.compareTo(new BigDecimal(property.get("minimum").toString())) < 0) {
            throw new IllegalArgumentException(field + " 小于允许的最小值");
        }
        if (property.get("maximum") != null
                && value.compareTo(new BigDecimal(property.get("maximum").toString())) > 0) {
            throw new IllegalArgumentException(field + " 大于允许的最大值");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("无效的tool参数Schema: " + field);
        }
        return (Map<String, Object>) map;
    }

    private Collection<?> asCollection(Object value) {
        return value instanceof Collection<?> collection ? collection : Set.of();
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
