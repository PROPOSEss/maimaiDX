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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
}
