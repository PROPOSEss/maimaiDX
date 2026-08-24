package com.maimai.maidx.assistant.tool;

import java.util.Map;
import java.util.Objects;

public record AssistantToolDefinition(String name,
                                      String description,
                                      Map<String, Object> parameterSchema) {

    public AssistantToolDefinition {
        if (name == null || !name.matches("[a-z][a-z0-9_]{2,63}")) {
            throw new IllegalArgumentException("invalid tool name: " + name);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("tool description must not be blank");
        }
        parameterSchema = Map.copyOf(Objects.requireNonNull(parameterSchema, "parameterSchema"));
    }
}
