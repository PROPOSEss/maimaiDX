package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.dto.AssistantStructuredData;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.service.AssistantBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RandomRecommendationTool extends AssistantToolSupport implements AssistantTool {

    private static final AssistantToolDefinition DEFINITION = new AssistantToolDefinition(
            AssistantToolNames.RANDOM_RECOMMENDATION,
            "按照可选的谱面定数范围随机推荐不同歌曲，count可选，默认5，最大10。",
            ToolSchemas.object(randomProperties()));

    private final AssistantBusinessService businessService;

    @Override
    public AssistantToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public IntentType getIntentType() {
        return IntentType.RANDOM_RECOMMENDATION;
    }

    @Override
    public ToolResult execute(AssistantUserContext userContext, Map<String, Object> params) {
        int count = intParam(params, "count", 5);
        BigDecimal minConstant = decimalParam(params, "minConstant");
        BigDecimal maxConstant = decimalParam(params, "maxConstant");
        if (minConstant != null && maxConstant != null && minConstant.compareTo(maxConstant) > 0) {
            throw new IllegalArgumentException("minConstant 不能大于 maxConstant");
        }

        AssistantBusinessService.RecommendationBundle result =
                businessService.getRandomRecommendations(minConstant, maxConstant, count);
        AssistantStructuredData data = new AssistantStructuredData();
        data.setRecommendations(result.charts().stream()
                .map(chart -> toRecommendationItem(chart, result.songs().get(chart.getSongId())))
                .toList());
        return ToolResult.success(
                DEFINITION.name(),
                getIntentType(),
                "已随机推荐" + data.getRecommendations().size() + "首不同歌曲。",
                data);
    }

    private static Map<String, Object> randomProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("count", ToolSchemas.integer(1, 10, "推荐歌曲数量"));
        properties.put("minConstant", ToolSchemas.number(1.0, 15.9, "最低谱面定数"));
        properties.put("maxConstant", ToolSchemas.number(1.0, 15.9, "最高谱面定数"));
        return properties;
    }
}
