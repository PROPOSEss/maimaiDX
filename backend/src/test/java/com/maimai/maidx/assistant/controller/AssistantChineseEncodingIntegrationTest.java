package com.maimai.maidx.assistant.controller;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.context.AssistantUserContextResolver;
import com.maimai.maidx.assistant.context.UserIdentitySource;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.service.AssistantAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantChineseEncodingIntegrationTest {

    @Test
    void responsePreservesChineseAnswerAndStructuredAdvice() throws Exception {
        AssistantAgentService service = mock(AssistantAgentService.class);
        AssistantUserContextResolver resolver = mock(AssistantUserContextResolver.class);
        AssistantUserContext context =
                new AssistantUserContext(999L, UserIdentitySource.DEVELOPMENT_HEADER);
        when(resolver.resolve(999L, null)).thenReturn(context);
        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(999L);
        response.setIntent(IntentType.TRAINING_ADVICE);
        response.setAnswer("已根据真实成绩生成三条训练建议。");
        AssistantQueryResponse.TrainingSuggestionItem item =
                new AssistantQueryResponse.TrainingSuggestionItem();
        item.setTitle("关注近期发挥稳定性");
        response.getStructuredData().getSuggestions().add(item);
        when(service.query(any(), any())).thenReturn(response);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AssistantController(service, resolver)).build();

        MvcResult result = mockMvc.perform(post("/assistant/query")
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"message\":\"请给我训练建议\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String text = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).contains("已根据真实成绩生成三条训练建议", "关注近期发挥稳定性");
    }
}
