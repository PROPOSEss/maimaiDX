package com.maimai.maidx.assistant.dto;

import java.util.List;

/**
 * Reserved extension point for future multi-turn memory. The first agent version always uses empty().
 */
public record AssistantConversationContext(String conversationId, List<Message> messages) {

    public AssistantConversationContext {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static AssistantConversationContext empty() {
        return new AssistantConversationContext(null, List.of());
    }

    public record Message(String role, String content) {
    }
}
