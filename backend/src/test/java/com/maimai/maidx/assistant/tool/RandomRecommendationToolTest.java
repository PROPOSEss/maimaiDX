package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.service.SongService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RandomRecommendationToolTest {

    @Mock
    private SongDifficultyRepository songDifficultyRepository;

    @Mock
    private SongService songService;

    @Test
    void returnsDistinctSongs() {
        RandomRecommendationTool tool = new RandomRecommendationTool(songDifficultyRepository, songService);
        SongDifficulty chartA = chart(10L, 100L, "13.0");
        SongDifficulty chartAAnotherDifficulty = chart(11L, 100L, "13.2");
        SongDifficulty chartB = chart(12L, 101L, "13.1");
        when(songDifficultyRepository.selectList(any())).thenReturn(List.of(chartA, chartAAnotherDifficulty, chartB));
        when(songService.listByIds(anyCollection())).thenReturn(List.of(song(100L, "mvp001"), song(101L, "mvp002")));
        ParsedIntent intent = new ParsedIntent();
        intent.setIntent(IntentType.RANDOM_RECOMMENDATION);
        intent.setCount(3);
        intent.setMinConstant(new BigDecimal("12.7"));
        intent.setMaxConstant(new BigDecimal("13.4"));

        AssistantQueryResponse response = tool.execute(999L, intent);

        assertThat(response.getRecommendations()).hasSize(2);
        assertThat(response.getRecommendations().stream().map(AssistantQueryResponse.RecommendationItem::getSongId))
                .doesNotHaveDuplicates();
    }

    @Test
    void returnsActualCountWhenCandidatesAreInsufficient() {
        RandomRecommendationTool tool = new RandomRecommendationTool(songDifficultyRepository, songService);
        SongDifficulty chartA = chart(10L, 100L, "13.0");
        when(songDifficultyRepository.selectList(any())).thenReturn(List.of(chartA));
        when(songService.listByIds(anyCollection())).thenReturn(List.of(song(100L, "mvp001")));
        ParsedIntent intent = new ParsedIntent();
        intent.setIntent(IntentType.RANDOM_RECOMMENDATION);
        intent.setCount(5);

        AssistantQueryResponse response = tool.execute(999L, intent);

        assertThat(response.getRecommendations()).hasSize(1);
    }

    private SongDifficulty chart(Long id, Long songId, String constant) {
        SongDifficulty chart = new SongDifficulty();
        chart.setId(id);
        chart.setSongId(songId);
        chart.setDifficulty(3);
        chart.setLevelDecimal(new BigDecimal(constant));
        return chart;
    }

    private Song song(Long id, String publicSongId) {
        Song song = new Song();
        song.setId(id);
        song.setSongId(publicSongId);
        song.setTitle("Demo " + publicSongId);
        return song;
    }
}
