package com.maimai.maidx.assistant.controller;

import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.service.AssistantService;
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
    private AssistantService assistantService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AssistantController(assistantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void queryReturnsAssistantResponse() throws Exception {
        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(999L);
        response.setIntent(IntentType.RECENT_SCORES);
        response.setAnswer("已查询最近20条成绩。");
        when(assistantService.query(eq(999L), any())).thenReturn(response);

        mockMvc.perform(post("/assistant/query")
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"最近20条成绩\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(999))
                .andExpect(jsonPath("$.data.intent").value("RECENT_SCORES"));
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
        when(assistantService.query(eq(999L), any())).thenReturn(response);

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
