package com.maimai.maidx.assistant.service;

import com.maimai.maidx.assistant.context.AssistantUserContextResolver;
import com.maimai.maidx.assistant.dto.AssistantQueryRequest;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Compatibility adapter for callers that still use the pre-agent AssistantService contract.
 */
@Service
@RequiredArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    private final AssistantUserContextResolver userContextResolver;
    private final AssistantAgentService assistantAgentService;

    @Override
    public AssistantQueryResponse query(Long headerUserId, AssistantQueryRequest request) {
        return assistantAgentService.query(userContextResolver.resolve(headerUserId, null), request);
    }
}
