package com.maimai.maidx.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.maimai.maidx.assistant.agent.*;
import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.context.UserIdentitySource;
import com.maimai.maidx.assistant.dto.AssistantConversationContext;
import com.maimai.maidx.assistant.enums.ParserSource;
import com.maimai.maidx.assistant.llm.LlmClient;
import com.maimai.maidx.assistant.tool.AssistantToolRegistry;
import com.maimai.maidx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import java.util.Map;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class AssistantValidationIT extends ValidationTestSupport {
    @Autowired AssistantToolRegistry registry;
    @Autowired RuleBasedAssistantAgentPlanner rules;
    @Autowired AssistantAnswerGenerator answers;
    @Autowired UserRepository users;

    @BeforeEach
    void scoresForKnownUsers() {
        mvp.importScores(1L, request(item(1, "98.00"), item(2, "99.00")));
        mvp.importScores(1L, request(item(1, "100.00"), item(3, "97.00")));
        mvp.importScores(2L, request(item(4, "100.50")));
        jdbc.update("UPDATE score_record SET last_play_time=DATE_ADD('2026-01-01',INTERVAL id SECOND)");
    }

    JsonNode query(String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1");
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.postForEntity("/assistant/query",
                new HttpEntity<>(Map.of("message", message), headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("code").asInt()).isEqualTo(200);
        assertThat(response.getBody().path("data").path("parserSource").asText()).isEqualTo("RULE");
        return response.getBody().path("data");
    }

    @Test
    void recentToolReturnsActualLatestUserRowsThroughHttp() {
        var response = query("最近2条成绩");
        assertThat(response.path("tool").path("name").asText()).isEqualTo("recent_scores");
        var scores = response.path("structuredData").path("scores");
        assertThat(scores.size()).isEqualTo(2);
        assertThat(scores.get(0).path("songId").asText()).isEqualTo(code(3));
        assertThat(scores.get(1).path("songId").asText()).isEqualTo(code(1));
        assertThat(response.path("answer").asText()).contains("2");
    }

    @Test
    void topToolDeduplicatesAcrossBatchesAndDoesNotLeakOtherUser() {
        var response = query("最高3条成绩");
        assertThat(response.path("tool").path("name").asText()).isEqualTo("top_scores");
        var scores = response.path("structuredData").path("scores");
        assertThat(scores.size()).isEqualTo(3);
        assertThat(scores.get(0).path("songId").asText()).isEqualTo(code(1));
        assertThat(scores.get(0).path("achievement").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(scores.get(1).path("songId").asText()).isEqualTo(code(2));
        assertThat(scores.get(2).path("songId").asText()).isEqualTo(code(3));
    }

    @Test
    void randomToolReturnsDistinctSongsFromRequestedConstantRange() {
        var response = query("随机推荐3首定数13到13的歌曲");
        var selected = response.path("structuredData").path("recommendations");
        assertThat(selected.size()).isEqualTo(3);
        var ids = new java.util.HashSet<String>();
        selected.forEach(song -> {
            assertThat(song.path("constant").decimalValue()).isEqualByComparingTo("13.0");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM song WHERE song_id=?", Integer.class,
                    song.path("songId").asText())).isEqualTo(1);
            ids.add(song.path("songId").asText());
        });
        assertThat(ids).hasSize(3);
    }

    @Test
    void trainingToolUsesKnownScoresAndBackendEvidence() {
        var response = query("给我5条训练建议");
        assertThat(response.path("structuredData").path("suggestions").size()).isEqualTo(5);
        assertThat(response.path("structuredData").path("suggestions").toString()).contains("98.500");
        var evidence = response.path("structuredData").path("evidence");
        assertThat(evidence.size()).isEqualTo(4);
        evidence.forEach(score -> assertThat(jdbc.queryForObject("SELECT user_id FROM score_record WHERE id=?",
                Long.class, score.path("recordId").asLong())).isEqualTo(1L));
    }

    @Test
    void searchToolReturnsMatchingDatabaseSong() {
        var response = query("搜索歌曲Validation Song 120");
        assertThat(response.path("tool").path("name").asText()).isEqualTo("song_search");
        assertThat(response.path("structuredData").path("songs").size()).isEqualTo(1);
        assertThat(response.path("structuredData").path("songs").get(0).path("songId").asText()).isEqualTo(code(120));
    }

    static Stream<String> invalidOutputs() {
        return Stream.of(
                "{invalid-json",
                "{\"toolName\":\"not_a_tool\",\"arguments\":{}}",
                "{\"toolName\":\"recent_scores\",\"arguments\":{\"limit\":\"two\"}}",
                "{\"toolName\":\"recent_scores\",\"arguments\":{\"limit\":51}}",
                "{\"toolName\":\"song_search\",\"arguments\":{}}",
                "{\"toolName\":\"recent_scores\",\"arguments\":{\"userId\":2}}",
                "{\"toolName\":\"recent_scores\",\"arguments\":{},\"sql\":\"SELECT\"}");
    }

    LlmAssistantAgentPlanner planner(String output, boolean available, boolean fail) {
        LlmClient fake = new LlmClient() {
            public boolean isAvailable() { return available; }
            public String chat(String system, String user) {
                if (fail) throw new IllegalStateException("local validation service unavailable");
                return output;
            }
        };
        return new LlmAssistantAgentPlanner(fake, rules, registry, mapper);
    }

    @ParameterizedTest
    @MethodSource("invalidOutputs")
    void invalidPlannerOutputFallsBackAndExecutesOnlySafeUserQuery(String output) {
        var planner = planner(output, true, false);
        var agent = new com.maimai.maidx.assistant.service.AssistantAgentServiceImpl(users, planner, registry, answers);
        var request = new com.maimai.maidx.assistant.dto.AssistantQueryRequest();
        request.setMessage("最近2条成绩");
        var response = agent.query(new AssistantUserContext(1L, UserIdentitySource.DEVELOPMENT_HEADER), request,
                AssistantConversationContext.empty());
        assertThat(response.getParserSource()).isEqualTo(ParserSource.RULE);
        assertThat(response.getTool().getName()).isEqualTo("recent_scores");
        assertThat(response.getScores()).hasSize(2).allSatisfy(score ->
                assertThat(jdbc.queryForObject("SELECT user_id FROM score_record WHERE id=?", Long.class,
                        score.getRecordId())).isEqualTo(1L));
        assertThat(count("score_snapshot")).isEqualTo(3);
    }

    @Test
    void localModelTransportFailureFallsBackWithoutExternalCall() {
        var plan = planner(null, true, true).plan("最近2条成绩", AssistantConversationContext.empty());
        assertThat(plan.source()).isEqualTo(ParserSource.RULE);
        assertThat(plan.toolCalls()).singleElement().satisfies(call -> assertThat(call.toolName()).isEqualTo("recent_scores"));
    }

    @Test
    void configuredUnavailableModelUsesRules() {
        assertThat(planner(null, false, false).plan("最近2条成绩", AssistantConversationContext.empty()).source())
                .isEqualTo(ParserSource.RULE);
    }

    @Test
    void toolExecutionErrorIsRejectedInsteadOfBeingReportedAsPlannerFallback() {
        var plan = planner("{\"toolName\":\"random_recommendation\",\"arguments\":{\"minConstant\":14,\"maxConstant\":13}}",
                true, false).plan("随机推荐", AssistantConversationContext.empty());
        assertThat(plan.source()).isEqualTo(ParserSource.LLM);
        assertThatThrownBy(() -> registry.execute(plan.toolCalls().get(0).toolName(),
                new AssistantUserContext(1L, UserIdentitySource.DEVELOPMENT_HEADER), plan.toolCalls().get(0).arguments()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能大于");
    }

    @Test
    void trailingSecondJsonObjectMustBeRejectedByStrictPlanner() {
        var plan = planner("{\"toolName\":\"recent_scores\",\"arguments\":{\"limit\":1}} {}", true, false)
                .plan("最近2条成绩", AssistantConversationContext.empty());
        assertThat(plan.source()).isEqualTo(ParserSource.RULE);
        assertThat(plan.toolCalls().get(0).arguments()).containsEntry("limit", 2);
    }
}
