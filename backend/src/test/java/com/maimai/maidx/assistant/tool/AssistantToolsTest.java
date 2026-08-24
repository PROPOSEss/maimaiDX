package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.context.UserIdentitySource;
import com.maimai.maidx.assistant.enums.AdviceSource;
import com.maimai.maidx.assistant.enums.TrainingFocusType;
import com.maimai.maidx.assistant.service.AssistantBusinessService;
import com.maimai.maidx.entity.Song;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantToolsTest {

    private final AssistantUserContext context =
            new AssistantUserContext(7L, UserIdentitySource.DEVELOPMENT_HEADER);

    @Test
    void scoreToolsDelegateQueriesToBusinessService() {
        AssistantBusinessService service = mock(AssistantBusinessService.class);
        AssistantBusinessService.ScoreBundle empty =
                new AssistantBusinessService.ScoreBundle(List.of(), Map.of(), Map.of());
        when(service.getRecentScores(7L, 20)).thenReturn(empty);
        when(service.getTopScores(7L, 4)).thenReturn(empty);

        ToolResult recent = new RecentScoresTool(service).execute(context, Map.of());
        ToolResult top = new TopScoresTool(service).execute(context, Map.of("limit", 4));

        assertThat(recent.toolName()).isEqualTo(AssistantToolNames.RECENT_SCORES);
        assertThat(top.toolName()).isEqualTo(AssistantToolNames.TOP_SCORES);
        verify(service).getRecentScores(7L, 20);
        verify(service).getTopScores(7L, 4);
    }

    @Test
    void recommendationAndTrainingToolsDelegateToBusinessService() {
        AssistantBusinessService service = mock(AssistantBusinessService.class);
        when(service.getRandomRecommendations(null, null, 5)).thenReturn(
                new AssistantBusinessService.RecommendationBundle(List.of(), Map.of()));
        AssistantBusinessService.ScoreBundle evidence =
                new AssistantBusinessService.ScoreBundle(List.of(), Map.of(), Map.of());
        AssistantBusinessService.TrainingSuggestion suggestion =
                new AssistantBusinessService.TrainingSuggestion(
                        TrainingFocusType.RECENT_CONSISTENCY, "稳定", "有波动", "复练");
        when(service.getTrainingAdvice(7L, 3)).thenReturn(
                new AssistantBusinessService.TrainingAdviceBundle(
                        AdviceSource.RULE, List.of(suggestion), evidence));

        ToolResult recommendation = new RandomRecommendationTool(service).execute(context, Map.of());
        ToolResult training = new TrainingAdviceTool(service).execute(context, Map.of());

        assertThat(recommendation.toolName()).isEqualTo(AssistantToolNames.RANDOM_RECOMMENDATION);
        assertThat(training.data().getSuggestions()).singleElement()
                .satisfies(item -> assertThat(item.getTitle()).isEqualTo("稳定"));
        verify(service).getRandomRecommendations(null, null, 5);
        verify(service).getTrainingAdvice(7L, 3);
    }

    @Test
    void songSearchConvertsBusinessResult() {
        AssistantBusinessService service = mock(AssistantBusinessService.class);
        Song song = new Song();
        song.setId(1L);
        song.setSongId("1001");
        song.setTitle("PANDORA PARADOXXX");
        when(service.searchSongs("PANDORA", 10)).thenReturn(List.of(song));

        ToolResult result = new SongSearchTool(service).execute(
                context, Map.of("keyword", "PANDORA"));

        assertThat(result.data().getSongs()).singleElement()
                .satisfies(item -> assertThat(item.getTitle()).isEqualTo("PANDORA PARADOXXX"));
        verify(service).searchSongs("PANDORA", 10);
    }
}
