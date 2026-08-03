package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecentScoresToolTest {

    @Mock
    private ScoreRecordRepository scoreRecordRepository;

    @Mock
    private SongDifficultyRepository songDifficultyRepository;

    @Mock
    private SongService songService;

    @Test
    void returnsRecentScoresWithSongAndChartInfo() {
        RecentScoresTool tool = new RecentScoresTool(scoreRecordRepository, songDifficultyRepository, songService);
        ScoreRecord record = scoreRecord(1L, 10L, 100L, "99.500", LocalDateTime.of(2026, 7, 1, 10, 0));
        SongDifficulty chart = chart(10L, 100L, 3, "13.2");
        Song song = song(100L, "mvp001", "Demo Song");
        when(scoreRecordRepository.selectList(any())).thenReturn(List.of(record));
        when(songDifficultyRepository.selectBatchIds(anyCollection())).thenReturn(List.of(chart));
        when(songService.listByIds(anyCollection())).thenReturn(List.of(song));
        ParsedIntent intent = new ParsedIntent();
        intent.setIntent(IntentType.RECENT_SCORES);
        intent.setLimit(20);

        AssistantQueryResponse response = tool.execute(999L, intent);

        assertThat(response.getScores()).hasSize(1);
        assertThat(response.getScores().get(0).getChartId()).isEqualTo(10L);
        assertThat(response.getScores().get(0).getSongName()).isEqualTo("Demo Song");
        assertThat(response.getScores().get(0).getAchievement()).isEqualByComparingTo(new BigDecimal("99.500"));
    }

    @Test
    void queriesCurrentUserScoresWithPlayedAtOrderAndLimit() {
        RecentScoresTool tool = new RecentScoresTool(scoreRecordRepository, songDifficultyRepository, songService);
        when(scoreRecordRepository.selectList(any())).thenReturn(List.of());
        ParsedIntent intent = new ParsedIntent();
        intent.setIntent(IntentType.RECENT_SCORES);
        intent.setLimit(20);

        AssistantQueryResponse response = tool.execute(999L, intent);

        assertThat(response.getScores()).isEmpty();
        verify(scoreRecordRepository).selectList(any());
    }

    private ScoreRecord scoreRecord(Long id, Long chartId, Long songId, String achievement, LocalDateTime playedAt) {
        ScoreRecord record = new ScoreRecord();
        record.setId(id);
        record.setUserId(999L);
        record.setDifficultyId(chartId);
        record.setSongId(songId);
        record.setAchievementRate(new BigDecimal(achievement));
        record.setLastPlayTime(playedAt);
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
}
