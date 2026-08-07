package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.AdviceSource;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.enums.TrainingFocusType;
import com.maimai.maidx.assistant.llm.LlmClient;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.service.SongService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingAdviceToolTest {

    @Mock
    private ScoreRecordRepository scoreRecordRepository;

    @Mock
    private SongDifficultyRepository songDifficultyRepository;

    @Mock
    private SongService songService;

    @Test
    void buildsTrainingProfileFromRealScoreRecords() {
        TrainingAdviceTool tool = tool(FakeLlmClient.unavailable());
        stubScoreData();

        var context = tool.buildTrainingContext(999L);

        assertThat(context.profile().getRecentScores()).hasSize(3);
        assertThat(context.profile().getTopScores()).isNotEmpty();
        assertThat(context.profile().getImprovementCandidates()).isNotEmpty();
        assertThat(context.profile().getAverageAchievement()).isEqualByComparingTo("99.400");
        assertThat(context.profile().getRecentScores().get(0).getSongName()).isEqualTo("Demo Future Bass");
        assertThat(context.evidence()).isNotEmpty();
    }

    @Test
    void returnsLlmTrainingAdviceWhenJsonIsValid() {
        FakeLlmClient llmClient = FakeLlmClient.available("""
                {"focusTypes":["IMPROVEMENT_CANDIDATE","CONSTANT_STABILITY","RECENT_CONSISTENCY"]}
                """);
        TrainingAdviceTool tool = tool(llmClient);
        stubScoreData();

        AssistantQueryResponse response = tool.execute(999L, intent());

        assertThat(response.getAdviceSource()).isEqualTo(AdviceSource.LLM);
        assertThat(response.getSuggestions()).hasSize(3);
        assertThat(response.getEvidence()).isNotEmpty();
        assertThat(response.getAnswer()).contains("模型选择的训练方向");
        assertThat(response.getSuggestions())
                .extracting(AssistantQueryResponse.TrainingSuggestionItem::getFocusType)
                .containsExactly(
                        TrainingFocusType.IMPROVEMENT_CANDIDATE,
                        TrainingFocusType.CONSTANT_STABILITY,
                        TrainingFocusType.RECENT_CONSISTENCY);
        assertThat(llmClient.lastUserContent())
                .contains("recentCount", "averageAchievement", "improvementCandidateCount")
                .doesNotContain("Demo Future Bass", "Edge of Rating", "songName", "recentScores");
    }

    @Test
    void truncatesLlmSuggestionsToRequestedAdviceCount() {
        TrainingAdviceTool tool = tool(FakeLlmClient.available("""
                {"focusTypes":["IMPROVEMENT_CANDIDATE","CONSTANT_STABILITY","RECENT_CONSISTENCY","B50_EDGE","TOP_SCORE_STABILITY"]}
                """));
        stubScoreData();
        ParsedIntent intent = intent();
        intent.setAdviceCount(3);

        AssistantQueryResponse response = tool.execute(999L, intent);

        assertThat(response.getAdviceSource()).isEqualTo(AdviceSource.LLM);
        assertThat(response.getSuggestions())
                .extracting(AssistantQueryResponse.TrainingSuggestionItem::getFocusType)
                .containsExactly(
                        TrainingFocusType.IMPROVEMENT_CANDIDATE,
                        TrainingFocusType.CONSTANT_STABILITY,
                        TrainingFocusType.RECENT_CONSISTENCY);
    }

    @Test
    void fallsBackToRuleAdviceWhenLlmAdviceJsonIsInvalid() {
        TrainingAdviceTool tool = tool(FakeLlmClient.available("""
                {"focusTypes":["IMPROVEMENT_CANDIDATE"]}
                """));
        stubScoreData();

        AssistantQueryResponse response = tool.execute(999L, intent());

        assertThat(response.getAdviceSource()).isEqualTo(AdviceSource.RULE);
        assertThat(response.getSuggestions()).hasSize(3);
        assertThat(response.getEvidence()).isNotEmpty();
    }

    @Test
    void fallsBackToRuleAdviceWhenLlmAdviceContainsExecutableInstruction() {
        TrainingAdviceTool tool = tool(FakeLlmClient.available("""
                {"focusTypes":["IMPROVEMENT_CANDIDATE","CONSTANT_STABILITY","RECENT_CONSISTENCY"],"sql":"select * from score_record"}
                """));
        stubScoreData();

        AssistantQueryResponse response = tool.execute(999L, intent());

        assertThat(response.getAdviceSource()).isEqualTo(AdviceSource.RULE);
        assertThat(response.getAnswer()).contains("规则训练建议");
    }

    @Test
    void rejectsMismatchedConstantForDemoFutureBass() {
        TrainingAdviceTool tool = tool(FakeLlmClient.available("""
                {"summary":"Demo Future Bass 14.4 达成率98.94%","suggestions":[]}
                """));
        stubScoreData();

        AssistantQueryResponse response = tool.execute(999L, intent());

        assertThat(response.getAdviceSource()).isEqualTo(AdviceSource.RULE);
        assertThat(responseText(response)).doesNotContain("Demo Future Bass 14.4", "98.94");
    }

    @Test
    void rejectsNonexistentConstantForEdgeOfRating() {
        TrainingAdviceTool tool = tool(FakeLlmClient.available("""
                {"summary":"Edge of Rating 13.1 达成率99.20%","suggestions":[]}
                """));
        stubScoreData();

        AssistantQueryResponse response = tool.execute(999L, intent());

        assertThat(response.getAdviceSource()).isEqualTo(AdviceSource.RULE);
        assertThat(responseText(response)).doesNotContain("Edge of Rating 13.1", "99.20");
    }

    @Test
    void rejectsFieldsCombinedFromDifferentSongs() {
        TrainingAdviceTool tool = tool(FakeLlmClient.available("""
                {"focusTypes":["IMPROVEMENT_CANDIDATE","CONSTANT_STABILITY","RECENT_CONSISTENCY"],
                 "claimedFact":{"songName":"Demo Future Bass","constant":14.2,"achievement":99.1}}
                """));
        stubScoreData();

        AssistantQueryResponse response = tool.execute(999L, intent());

        assertThat(response.getAdviceSource()).isEqualTo(AdviceSource.RULE);
        assertThat(responseText(response)).doesNotContain("Demo Future Bass", "14.2");
    }

    @Test
    void evidenceAlwaysUsesBackendScoreTuples() {
        TrainingAdviceTool tool = tool(FakeLlmClient.available("""
                {"focusTypes":["IMPROVEMENT_CANDIDATE","CONSTANT_STABILITY","RECENT_CONSISTENCY"]}
                """));
        stubScoreData();

        AssistantQueryResponse response = tool.execute(999L, intent());

        assertThat(response.getEvidence().get(0).getSongName()).isEqualTo("Demo Future Bass");
        assertThat(response.getEvidence().get(0).getChartId()).isEqualTo(10L);
        assertThat(response.getEvidence().get(0).getConstant()).isEqualByComparingTo("13.8");
        assertThat(response.getEvidence().get(0).getAchievement()).isEqualByComparingTo("99.800");
        assertThat(response.getEvidence().get(0).getRa()).isEqualTo(250);
    }

    @Test
    void returnsRuleResponseWhenScoresAreMissing() {
        TrainingAdviceTool tool = tool(FakeLlmClient.available("""
                {"summary":"should not be used","suggestions":[]}
                """));
        when(scoreRecordRepository.selectList(any())).thenReturn(List.of(), List.of(), List.of());

        AssistantQueryResponse response = tool.execute(999L, intent());

        assertThat(response.getAdviceSource()).isEqualTo(AdviceSource.RULE);
        assertThat(response.getAnswer()).contains("暂无可分析成绩");
        assertThat(response.getEvidence()).isEmpty();
    }

    @Test
    void responseKeepsCurrentUserId() {
        TrainingAdviceTool tool = tool(FakeLlmClient.unavailable());
        stubScoreData();

        AssistantQueryResponse response = tool.execute(7L, intent());

        assertThat(response.getUserId()).isEqualTo(7L);
        verify(scoreRecordRepository, atLeastOnce()).selectList(any());
    }

    private void stubScoreData() {
        List<ScoreRecord> recent = List.of(
                score(1L, 10L, 100L, "99.800", 250, LocalDateTime.of(2026, 8, 1, 10, 0)),
                score(2L, 11L, 100L, "99.100", 240, LocalDateTime.of(2026, 7, 31, 10, 0)),
                score(3L, 12L, 101L, "99.300", 245, LocalDateTime.of(2026, 7, 30, 10, 0))
        );
        List<ScoreRecord> top = List.of(
                score(1L, 10L, 100L, "99.800", 250, LocalDateTime.of(2026, 8, 1, 10, 0)),
                score(3L, 12L, 101L, "99.300", 245, LocalDateTime.of(2026, 7, 30, 10, 0))
        );
        List<ScoreRecord> edge = List.of(score(9L, 10L, 100L, "99.800", 250, LocalDateTime.of(2026, 8, 1, 10, 0)));
        when(scoreRecordRepository.selectList(any())).thenReturn(recent, top, edge);
        when(songDifficultyRepository.selectBatchIds(anyCollection())).thenReturn(List.of(
                chart(10L, 100L, 3, "13.8"),
                chart(11L, 101L, 3, "14.2"),
                chart(12L, 101L, 4, "14.8")
        ));
        when(songService.listByIds(anyCollection())).thenReturn(List.of(
                song(100L, "mvp001", "Demo Future Bass"),
                song(101L, "mvp002", "Edge of Rating")));
    }

    private TrainingAdviceTool tool(LlmClient llmClient) {
        return new TrainingAdviceTool(scoreRecordRepository, songDifficultyRepository, songService, llmClient,
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    }

    private ParsedIntent intent() {
        ParsedIntent intent = new ParsedIntent();
        intent.setIntent(IntentType.TRAINING_ADVICE);
        intent.setAdviceCount(3);
        return intent;
    }

    private ScoreRecord score(Long id, Long chartId, Long songId, String achievement, Integer ra, LocalDateTime playedAt) {
        ScoreRecord record = new ScoreRecord();
        record.setId(id);
        record.setUserId(999L);
        record.setDifficultyId(chartId);
        record.setSongId(songId);
        record.setAchievementRate(new BigDecimal(achievement));
        record.setRa(ra);
        record.setLastPlayTime(playedAt);
        record.setIsB50(1);
        return record;
    }

    private SongDifficulty chart(Long id, Long songId, Integer difficulty, String constant) {
        SongDifficulty chart = new SongDifficulty();
        chart.setId(id);
        chart.setSongId(songId);
        chart.setDifficulty(difficulty);
        chart.setLevelDecimal(new BigDecimal(constant));
        return chart;
    }

    private Song song(Long id, String publicSongId, String title) {
        Song song = new Song();
        song.setId(id);
        song.setSongId(publicSongId);
        song.setTitle(title);
        return song;
    }

    private static class FakeLlmClient implements LlmClient {
        private final boolean available;
        private final String response;
        private String lastUserContent;

        private FakeLlmClient(boolean available, String response) {
            this.available = available;
            this.response = response;
        }

        static FakeLlmClient unavailable() {
            return new FakeLlmClient(false, null);
        }

        static FakeLlmClient available(String response) {
            return new FakeLlmClient(true, response);
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String chat(String systemPrompt, String userContent) {
            lastUserContent = userContent;
            return response;
        }

        String lastUserContent() {
            return lastUserContent;
        }
    }

    private String responseText(AssistantQueryResponse response) {
        return response.getAnswer() + response.getSuggestions().stream()
                .map(item -> item.getTitle() + item.getReason() + item.getAction())
                .collect(java.util.stream.Collectors.joining());
    }
}
