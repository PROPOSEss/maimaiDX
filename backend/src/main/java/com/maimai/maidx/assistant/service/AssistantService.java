package com.maimai.maidx.assistant.service;

import com.maimai.maidx.assistant.dto.AssistantQueryRequest;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;

public interface AssistantService {

    AssistantQueryResponse query(Long headerUserId, AssistantQueryRequest request);
}
