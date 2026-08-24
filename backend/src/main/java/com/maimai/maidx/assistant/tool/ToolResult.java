package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.dto.AssistantStructuredData;
import com.maimai.maidx.assistant.enums.AdviceSource;
import com.maimai.maidx.assistant.enums.IntentType;

import java.util.Objects;

public record ToolResult(String toolName,
                         IntentType intent,
                         String summary,
                         AssistantStructuredData data,
                         AdviceSource adviceSource) {

    public ToolResult {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(summary, "summary");
        data = data == null ? new AssistantStructuredData() : data;
    }

    public static ToolResult success(String toolName,
                                     IntentType intent,
                                     String summary,
                                     AssistantStructuredData data) {
        return new ToolResult(toolName, intent, summary, data, null);
    }

    public static ToolResult success(String toolName,
                                     IntentType intent,
                                     String summary,
                                     AssistantStructuredData data,
                                     AdviceSource adviceSource) {
        return new ToolResult(toolName, intent, summary, data, adviceSource);
    }
}
