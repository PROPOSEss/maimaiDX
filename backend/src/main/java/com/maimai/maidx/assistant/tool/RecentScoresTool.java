package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.dto.AssistantStructuredData;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.service.AssistantBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RecentScoresTool extends AssistantToolSupport implements AssistantTool {

    private static final AssistantToolDefinition DEFINITION = new AssistantToolDefinition(
            AssistantToolNames.RECENT_SCORES,
            "查询当前登录用户最近游玩的成绩，limit可选，默认20，最大50。",
            ToolSchemas.object(Map.of(
                    "limit", ToolSchemas.integer(1, 50, "返回成绩数量"))));

    private final AssistantBusinessService businessService;

    @Override
    public AssistantToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public IntentType getIntentType() {
        return IntentType.RECENT_SCORES;
    }

    @Override
    public ToolResult execute(AssistantUserContext userContext, Map<String, Object> params) {
        int limit = intParam(params, "limit", 20);
        AssistantBusinessService.ScoreBundle result = businessService.getRecentScores(userContext.userId(), limit);
        AssistantStructuredData data = new AssistantStructuredData();
        data.setScores(result.records().stream()
                .map(record -> toScoreItem(record, result.songs(), result.charts()))
                .toList());
        return ToolResult.success(
                DEFINITION.name(),
                getIntentType(),
                "已查询最近" + data.getScores().size() + "条成绩。",
                data);
    }
}
