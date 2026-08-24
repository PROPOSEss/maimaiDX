package com.maimai.maidx.assistant.agent;

import com.maimai.maidx.assistant.tool.ToolResult;
public interface AssistantAnswerGenerator {

    String generate(ToolResult toolResult);
}
