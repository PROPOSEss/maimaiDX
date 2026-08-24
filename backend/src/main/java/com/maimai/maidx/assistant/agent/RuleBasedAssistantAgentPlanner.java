package com.maimai.maidx.assistant.agent;

import com.maimai.maidx.assistant.dto.AssistantConversationContext;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.ParserSource;
import com.maimai.maidx.assistant.parser.RuleBasedIntentParser;
import com.maimai.maidx.assistant.tool.AssistantToolNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RuleBasedAssistantAgentPlanner implements AssistantAgentPlanner {

    private final RuleBasedIntentParser intentParser;

    @Override
    public AssistantAgentPlan plan(String message, AssistantConversationContext conversationContext) {
        ParsedIntent intent = intentParser.parse(message);
        Map<String, Object> arguments = new LinkedHashMap<>();
        String toolName = switch (intent.getIntent()) {
            case RECENT_SCORES -> {
                putIfNotNull(arguments, "limit", intent.getLimit());
                yield AssistantToolNames.RECENT_SCORES;
            }
            case TOP_SCORES -> {
                putIfNotNull(arguments, "limit", intent.getLimit());
                yield AssistantToolNames.TOP_SCORES;
            }
            case RANDOM_RECOMMENDATION -> {
                putIfNotNull(arguments, "count", intent.getCount());
                putIfNotNull(arguments, "minConstant", intent.getMinConstant());
                putIfNotNull(arguments, "maxConstant", intent.getMaxConstant());
                yield AssistantToolNames.RANDOM_RECOMMENDATION;
            }
            case TRAINING_ADVICE -> {
                putIfNotNull(arguments, "adviceCount", intent.getAdviceCount());
                yield AssistantToolNames.TRAINING_ADVICE;
            }
            case SONG_SEARCH -> {
                putIfNotNull(arguments, "keyword", intent.getKeyword());
                putIfNotNull(arguments, "limit", intent.getLimit());
                yield AssistantToolNames.SONG_SEARCH;
            }
            case UNKNOWN -> null;
        };
        if (toolName == null) {
            return AssistantAgentPlan.unsupported(ParserSource.RULE);
        }
        return AssistantAgentPlan.single(new AssistantAgentToolCall(toolName, arguments), ParserSource.RULE);
    }

    private void putIfNotNull(Map<String, Object> arguments, String name, Object value) {
        if (value != null) {
            arguments.put(name, value);
        }
    }
}
