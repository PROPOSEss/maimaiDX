package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AssistantToolRouter {

    private final Map<IntentType, AssistantTool> toolMap = new EnumMap<>(IntentType.class);

    public AssistantToolRouter(List<AssistantTool> tools) {
        for (AssistantTool tool : tools) {
            toolMap.put(tool.supportIntent(), tool);
        }
    }

    public AssistantQueryResponse route(Long userId, ParsedIntent intent) {
        AssistantTool tool = toolMap.get(intent.getIntent());
        if (tool == null) {
            throw new IllegalArgumentException("暂不支持的助手意图: " + intent.getIntent());
        }
        return tool.execute(userId, intent);
    }
}
