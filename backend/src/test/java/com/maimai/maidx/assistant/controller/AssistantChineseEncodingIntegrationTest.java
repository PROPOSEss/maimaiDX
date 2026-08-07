package com.maimai.maidx.assistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.config.AssistantProperties;
import com.maimai.maidx.assistant.llm.LlmClient;
import com.maimai.maidx.assistant.parser.LlmIntentParser;
import com.maimai.maidx.assistant.parser.RuleBasedIntentParser;
import com.maimai.maidx.assistant.service.AssistantServiceImpl;
import com.maimai.maidx.assistant.tool.AssistantToolRouter;
import com.maimai.maidx.assistant.tool.TrainingAdviceTool;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.entity.User;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.repository.UserRepository;
import com.maimai.maidx.service.SongService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantChineseEncodingIntegrationTest {

    @Test
    void preservesFakeLlmChineseFromStrictJsonToHttpResponseBytes() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        QueueLlmClient llmClient = new QueueLlmClient(
                "{\"intent\":\"TRAINING_ADVICE\",\"adviceCount\":3}",
                """
                        {"focusTypes":["IMPROVEMENT_CANDIDATE","CONSTANT_STABILITY","RECENT_CONSISTENCY"]}
                        """
        );

        UserRepository userRepository = mock(UserRepository.class);
        ScoreRecordRepository scoreRecordRepository = mock(ScoreRecordRepository.class);
        SongDifficultyRepository songDifficultyRepository = mock(SongDifficultyRepository.class);
        SongService songService = mock(SongService.class);
        User user = new User();
        user.setId(999L);
        when(userRepository.selectById(999L)).thenReturn(user);

        ScoreRecord score = score();
        when(scoreRecordRepository.selectList(any())).thenReturn(List.of(score), List.of(score), List.of(score));
        when(songDifficultyRepository.selectBatchIds(anyCollection())).thenReturn(List.of(chart()));
        when(songService.listByIds(anyCollection())).thenReturn(List.of(song()));

        TrainingAdviceTool trainingAdviceTool = new TrainingAdviceTool(
                scoreRecordRepository, songDifficultyRepository, songService, llmClient, objectMapper);
        AssistantToolRouter router = new AssistantToolRouter(List.of(trainingAdviceTool));
        LlmIntentParser parser = new LlmIntentParser(llmClient, new RuleBasedIntentParser(), objectMapper);
        AssistantProperties properties = new AssistantProperties();
        AssistantServiceImpl service = new AssistantServiceImpl(properties, userRepository, parser, router);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AssistantController(service)).build();

        MvcResult result = mockMvc.perform(post("/assistant/query")
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"message\":\"根据最近成绩生成训练建议\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parserSource").value("LLM"))
                .andExpect(jsonPath("$.data.adviceSource").value("LLM"))
                .andReturn();

        String responseText = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(responseText)
                .contains("模型选择的训练方向", "优先复练接近提升线的谱面", "围绕常打定数稳定练习", "关注近期发挥稳定性")
                .doesNotContain("è¿", "å¼º");
        assertThat(llmClient.systemPrompts()).hasSize(2);
        assertThat(llmClient.systemPrompts().get(1)).contains("focusTypes must contain exactly 3 distinct items");
    }

    private ScoreRecord score() {
        ScoreRecord score = new ScoreRecord();
        score.setId(1L);
        score.setUserId(999L);
        score.setDifficultyId(10L);
        score.setSongId(100L);
        score.setAchievementRate(new BigDecimal("99.800"));
        score.setRa(250);
        score.setLastPlayTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        score.setIsB50(1);
        return score;
    }

    private SongDifficulty chart() {
        SongDifficulty chart = new SongDifficulty();
        chart.setId(10L);
        chart.setSongId(100L);
        chart.setDifficulty(3);
        chart.setLevelDecimal(new BigDecimal("13.2"));
        return chart;
    }

    private Song song() {
        Song song = new Song();
        song.setId(100L);
        song.setSongId("mvp001");
        song.setTitle("测试曲目");
        return song;
    }

    private static class QueueLlmClient implements LlmClient {
        private final Queue<String> responses;
        private final List<String> systemPrompts = new ArrayList<>();

        private QueueLlmClient(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String chat(String systemPrompt, String userContent) {
            systemPrompts.add(systemPrompt);
            return responses.remove();
        }

        private List<String> systemPrompts() {
            return systemPrompts;
        }
    }
}
