package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.dto.AssistantStructuredData;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.service.AssistantBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SongSearchTool extends AssistantToolSupport implements AssistantTool {

    private static final AssistantToolDefinition DEFINITION = new AssistantToolDefinition(
            AssistantToolNames.SONG_SEARCH,
            "按歌曲标题或曲师关键词搜索歌曲，keyword必填，limit可选，默认10，最大20。",
            ToolSchemas.object(searchProperties(), "keyword"));

    private final AssistantBusinessService businessService;

    @Override
    public AssistantToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public IntentType getIntentType() {
        return IntentType.SONG_SEARCH;
    }

    @Override
    public ToolResult execute(AssistantUserContext userContext, Map<String, Object> params) {
        String keyword = stringParam(params, "keyword").trim();
        int limit = intParam(params, "limit", 10);
        AssistantStructuredData data = new AssistantStructuredData();
        data.setSongs(businessService.searchSongs(keyword, limit).stream()
                .map(this::toSongItem)
                .toList());
        return ToolResult.success(
                DEFINITION.name(),
                getIntentType(),
                "已找到" + data.getSongs().size() + "首与“" + keyword + "”相关的歌曲。",
                data);
    }

    private static Map<String, Object> searchProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", ToolSchemas.string(1, 100, "歌曲标题或曲师关键词"));
        properties.put("limit", ToolSchemas.integer(1, 20, "返回歌曲数量"));
        return properties;
    }
}
