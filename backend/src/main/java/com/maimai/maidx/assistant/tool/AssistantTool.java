package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;

public interface AssistantTool {

    IntentType supportIntent();

    AssistantQueryResponse execute(Long userId, ParsedIntent intent);
}
