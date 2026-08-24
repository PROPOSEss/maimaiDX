package com.maimai.maidx.assistant.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.dto.AssistantConversationContext;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.enums.ParserSource;
import com.maimai.maidx.assistant.llm.LlmClient;
import com.maimai.maidx.assistant.parser.RuleBasedIntentParser;
import com.maimai.maidx.assistant.tool.AssistantTool;
import com.maimai.maidx.assistant.tool.AssistantToolDefinition;
import com.maimai.maidx.assistant.tool.AssistantToolNames;
import com.maimai.maidx.assistant.tool.AssistantToolRegistry;
import com.maimai.maidx.assistant.tool.ToolResult;
import com.maimai.maidx.assistant.tool.ToolSchemaValidator;
import com.maimai.maidx.assistant.tool.ToolSchemas;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAssistantAgentPlannerTest {

    @Test
    void parsesStrictSingleToolJson() {
        LlmAssistantAgentPlanner planner = planner(new FakeLlmClient(true,
                "{\"toolName\":\"recent_scores\",\"arguments\":{\"limit\":5}}"));

        AssistantAgentPlan plan = planner.plan("最近5条成绩", AssistantConversationContext.empty());

        assertThat(plan.source()).isEqualTo(ParserSource.LLM);
        assertThat(plan.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.toolName()).isEqualTo(AssistantToolNames.RECENT_SCORES);
            assertThat(call.arguments()).containsEntry("limit", 5);
        });
    }

    @Test
    void rejectsIdentityArgumentAndFallsBackToRules() {
        LlmAssistantAgentPlanner planner = planner(new FakeLlmClient(true,
                "{\"toolName\":\"recent_scores\",\"arguments\":{\"limit\":5,\"userId\":99}}"));

        AssistantAgentPlan plan = planner.plan("最近5条成绩", AssistantConversationContext.empty());

        assertThat(plan.source()).isEqualTo(ParserSource.RULE);
        assertThat(plan.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.arguments()).doesNotContainKey("userId"));
    }

    @Test
    void usesRulePlannerWhenLlmUnavailable() {
        LlmAssistantAgentPlanner planner = planner(new FakeLlmClient(false, null));

        AssistantAgentPlan plan = planner.plan("搜索歌曲PANDORA", AssistantConversationContext.empty());

        assertThat(plan.source()).isEqualTo(ParserSource.RULE);
        assertThat(plan.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.toolName()).isEqualTo(AssistantToolNames.SONG_SEARCH);
            assertThat(call.arguments()).containsEntry("keyword", "pandora");
        });
    }

    private LlmAssistantAgentPlanner planner(LlmClient llmClient) {
        AssistantTool tool = new AssistantTool() {
            @Override
            public AssistantToolDefinition getDefinition() {
                return new AssistantToolDefinition(
                        AssistantToolNames.RECENT_SCORES,
                        "recent",
                        ToolSchemas.object(Map.of("limit", ToolSchemas.integer(1, 50, "limit"))));
            }

            @Override
            public IntentType getIntentType() {
                return IntentType.RECENT_SCORES;
            }

            @Override
            public ToolResult execute(AssistantUserContext userContext, Map<String, Object> params) {
                throw new UnsupportedOperationException();
            }
        };
        AssistantTool songTool = new AssistantTool() {
            @Override
            public AssistantToolDefinition getDefinition() {
                return new AssistantToolDefinition(
                        AssistantToolNames.SONG_SEARCH,
                        "search",
                        ToolSchemas.object(Map.of("keyword", ToolSchemas.string(1, 100, "keyword")), "keyword"));
            }

            @Override
            public IntentType getIntentType() {
                return IntentType.SONG_SEARCH;
            }

            @Override
            public ToolResult execute(AssistantUserContext userContext, Map<String, Object> params) {
                throw new UnsupportedOperationException();
            }
        };
        AssistantToolRegistry registry = new AssistantToolRegistry(
                List.of(tool, songTool), new ToolSchemaValidator());
        RuleBasedAssistantAgentPlanner fallback =
                new RuleBasedAssistantAgentPlanner(new RuleBasedIntentParser());
        return new LlmAssistantAgentPlanner(llmClient, fallback, registry, new ObjectMapper());
    }

    private record FakeLlmClient(boolean available, String response) implements LlmClient {
        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String chat(String systemPrompt, String userContent) {
            return response;
        }
    }
}
