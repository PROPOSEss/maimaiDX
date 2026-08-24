package com.maimai.maidx.assistant.agent;

import java.util.Map;
import java.util.Objects;

public record AssistantAgentToolCall(String toolName, Map<String, Object> arguments) {

    public AssistantAgentToolCall {
        Objects.requireNonNull(toolName, "toolName");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
