package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.AssistantStructuredData;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.service.AssistantBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class TrainingAdviceTool extends AssistantToolSupport implements AssistantTool {

    private static final AssistantToolDefinition DEFINITION = new AssistantToolDefinition(
            AssistantToolNames.TRAINING_ADVICE,
            "根据当前登录用户的真实成绩画像生成训练建议，adviceCount可选，默认3，最大5。",
            ToolSchemas.object(Map.of(
                    "adviceCount", ToolSchemas.integer(1, 5, "训练建议数量"))));

    private final AssistantBusinessService businessService;

    @Override
    public AssistantToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public IntentType getIntentType() {
        return IntentType.TRAINING_ADVICE;
    }

    @Override
    public ToolResult execute(AssistantUserContext userContext, Map<String, Object> params) {
        int adviceCount = intParam(params, "adviceCount", 3);
        AssistantBusinessService.TrainingAdviceBundle result =
                businessService.getTrainingAdvice(userContext.userId(), adviceCount);

        AssistantStructuredData data = new AssistantStructuredData();
        data.setSuggestions(result.suggestions().stream().map(suggestion -> {
            AssistantQueryResponse.TrainingSuggestionItem item = new AssistantQueryResponse.TrainingSuggestionItem();
            item.setFocusType(suggestion.focusType());
            item.setTitle(suggestion.title());
            item.setReason(suggestion.reason());
            item.setAction(suggestion.action());
            return item;
        }).toList());
        data.setEvidence(result.evidence().records().stream()
                .map(record -> toScoreItem(record, result.evidence().songs(), result.evidence().charts()))
                .toList());

        String summary = result.evidence().records().isEmpty()
                ? "当前用户暂无可分析成绩，请先导入成绩后再生成训练建议。"
                : "已根据最近真实成绩生成" + data.getSuggestions().size() + "条训练建议。";
        return ToolResult.success(
                DEFINITION.name(),
                getIntentType(),
                summary,
                data,
                result.source());
    }
}
