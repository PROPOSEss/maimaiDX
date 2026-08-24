package com.maimai.maidx.assistant.service;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.dto.AssistantConversationContext;
import com.maimai.maidx.assistant.dto.AssistantQueryRequest;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;

public interface AssistantAgentService {

    default AssistantQueryResponse query(AssistantUserContext userContext, AssistantQueryRequest request) {
        return query(userContext, request, AssistantConversationContext.empty());
    }

    AssistantQueryResponse query(AssistantUserContext userContext,
                                 AssistantQueryRequest request,
                                 AssistantConversationContext conversationContext);
}
