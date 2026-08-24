package com.maimai.maidx.assistant.controller;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.context.AssistantUserContextResolver;
import com.maimai.maidx.assistant.context.UserIdentitySource;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.AssistantStructuredData;
import com.maimai.maidx.assistant.dto.AssistantToolInfo;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.service.AssistantAgentService;
import com.maimai.maidx.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssistantControllerMockTest {

    private MockMvc mockMvc;

    @Mock
    private AssistantAgentService assistantAgentService;

    @Mock
    private AssistantUserContextResolver userContextResolver;

    private final AssistantUserContext userContext =
            new AssistantUserContext(999L, UserIdentitySource.DEVELOPMENT_HEADER);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AssistantController(assistantAgentService, userContextResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(userContextResolver.resolve(999L, null)).thenReturn(userContext);
    }

    @Test
    void queryReturnsAssistantResponse() throws Exception {
        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(999L);
        response.setIntent(IntentType.RECENT_SCORES);
        response.setAnswer("已查询最近20条成绩。");
        AssistantToolInfo tool = new AssistantToolInfo();
        tool.setName("recent_scores");
        tool.setSuccess(true);
        tool.setArguments(Map.of("limit", 20));
        response.setTool(tool);
        AssistantStructuredData structuredData = new AssistantStructuredData();
        structuredData.getScores().add(new AssistantQueryResponse.ScoreItem());
        response.setStructuredData(structuredData);
        when(assistantAgentService.query(eq(userContext), any())).thenReturn(response);

        mockMvc.perform(post("/assistant/query")
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"最近20条成绩\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(999))
                .andExpect(jsonPath("$.data.intent").value("RECENT_SCORES"))
                .andExpect(jsonPath("$.data.tool.name").value("recent_scores"))
                .andExpect(jsonPath("$.data.structuredData.scores.length()").value(1));
    }

    @Test
    void queryPreservesUtf8ChineseInRawResponseBytes() throws Exception {
        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(999L);
        response.setIntent(IntentType.TRAINING_ADVICE);
        response.setAnswer("根据最近成绩生成训练建议");
        AssistantQueryResponse.TrainingSuggestionItem suggestion = new AssistantQueryResponse.TrainingSuggestionItem();
        suggestion.setTitle("稳定练习");
        suggestion.setReason("近期达成率有波动");
        suggestion.setAction("每天练习三次");
        response.getSuggestions().add(suggestion);
        when(assistantAgentService.query(eq(userContext), any())).thenReturn(response);

        MvcResult result = mockMvc.perform(post("/assistant/query")
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"message\":\"根据最近成绩生成训练建议\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String responseText = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(responseText)
                .contains("根据最近成绩生成训练建议", "稳定练习", "近期达成率有波动", "每天练习三次")
                .doesNotContain("è¿", "å¼º");
    }
}
