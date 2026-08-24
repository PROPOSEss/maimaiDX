package com.maimai.maidx.assistant.agent;

import com.maimai.maidx.assistant.dto.AssistantConversationContext;

public interface AssistantAgentPlanner {

    AssistantAgentPlan plan(String message, AssistantConversationContext conversationContext);
}
