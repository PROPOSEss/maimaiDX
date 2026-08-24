package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.enums.IntentType;

import java.util.Map;

public interface AssistantTool {

    AssistantToolDefinition getDefinition();

    IntentType getIntentType();

    ToolResult execute(AssistantUserContext userContext, Map<String, Object> params);
}
