package com.maimai.maidx.assistant.agent;

import com.maimai.maidx.assistant.tool.ToolResult;
import org.springframework.stereotype.Component;

/**
 * Default answer generator that keeps private structured tool data inside the backend.
 */
@Component
public class DeterministicAssistantAnswerGenerator implements AssistantAnswerGenerator {

    @Override
    public String generate(ToolResult toolResult) {
        return toolResult.summary();
    }
}
