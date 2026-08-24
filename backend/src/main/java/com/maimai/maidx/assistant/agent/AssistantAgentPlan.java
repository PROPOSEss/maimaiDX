package com.maimai.maidx.assistant.agent;

import com.maimai.maidx.assistant.enums.ParserSource;

import java.util.List;
import java.util.Objects;

public record AssistantAgentPlan(List<AssistantAgentToolCall> toolCalls, ParserSource source) {

    public AssistantAgentPlan {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        Objects.requireNonNull(source, "source");
    }

    public static AssistantAgentPlan single(AssistantAgentToolCall toolCall, ParserSource source) {
        return new AssistantAgentPlan(List.of(toolCall), source);
    }

    public static AssistantAgentPlan unsupported(ParserSource source) {
        return new AssistantAgentPlan(List.of(), source);
    }
}
