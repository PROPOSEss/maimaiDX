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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopScoresToolTest {

    @Mock
    private ScoreRecordRepository scoreRecordRepository;

    @Mock
    private SongDifficultyRepository songDifficultyRepository;

    @Mock
    private SongService songService;

    @Test
    void keepsHighestAchievementForSameChart() {
        TopScoresTool tool = new TopScoresTool(scoreRecordRepository, songDifficultyRepository, songService);
        ScoreRecord low = scoreRecord(1L, 10L, 100L, "99.100", LocalDateTime.of(2026, 7, 1, 10, 0));
        ScoreRecord high = scoreRecord(2L, 10L, 100L, "100.000", LocalDateTime.of(2026, 7, 2, 10, 0));
        when(scoreRecordRepository.selectList(any())).thenReturn(List.of(low, high));
        when(songDifficultyRepository.selectBatchIds(anyCollection())).thenReturn(List.of(chart(10L, 100L)));
        when(songService.listByIds(anyCollection())).thenReturn(List.of(song(100L)));
        ParsedIntent intent = topIntent();

        AssistantQueryResponse response = tool.execute(999L, intent);

        assertThat(response.getScores()).hasSize(1);
        assertThat(response.getScores().get(0).getRecordId()).isEqualTo(2L);
        assertThat(response.getScores().get(0).getAchievement()).isEqualByComparingTo(new BigDecimal("100.000"));
    }

    @Test
    void keepsNewestRecordWhenAchievementTies() {
        TopScoresTool tool = new TopScoresTool(scoreRecordRepository, songDifficultyRepository, songService);
        ScoreRecord oldRecord = scoreRecord(1L, 10L, 100L, "99.500", LocalDateTime.of(2026, 7, 1, 10, 0));
        ScoreRecord newRecord = scoreRecord(2L, 10L, 100L, "99.500", LocalDateTime.of(2026, 7, 3, 10, 0));
        when(scoreRecordRepository.selectList(any())).thenReturn(List.of(oldRecord, newRecord));
        when(songDifficultyRepository.selectBatchIds(anyCollection())).thenReturn(List.of(chart(10L, 100L)));
        when(songService.listByIds(anyCollection())).thenReturn(List.of(song(100L)));
        ParsedIntent intent = topIntent();

        AssistantQueryResponse response = tool.execute(999L, intent);

        assertThat(response.getScores()).hasSize(1);
        assertThat(response.getScores().get(0).getRecordId()).isEqualTo(2L);
        assertThat(response.getScores().get(0).getPlayedAt()).isEqualTo(LocalDateTime.of(2026, 7, 3, 10, 0));
    }

    @Test
    void treatsNullAchievementAsLowestScore() {
        TopScoresTool tool = new TopScoresTool(scoreRecordRepository, songDifficultyRepository, songService);
        ScoreRecord nullAchievement = scoreRecord(1L, 10L, 100L, null, LocalDateTime.of(2026, 7, 3, 10, 0));
        ScoreRecord validAchievement = scoreRecord(2L, 10L, 100L, "99.000", LocalDateTime.of(2026, 7, 1, 10, 0));
        when(scoreRecordRepository.selectList(any())).thenReturn(List.of(nullAchievement, validAchievement));
        when(songDifficultyRepository.selectBatchIds(anyCollection())).thenReturn(List.of(chart(10L, 100L)));
        when(songService.listByIds(anyCollection())).thenReturn(List.of(song(100L)));
        ParsedIntent intent = topIntent();

        AssistantQueryResponse response = tool.execute(999L, intent);

        assertThat(response.getScores()).hasSize(1);
        assertThat(response.getScores().get(0).getRecordId()).isEqualTo(2L);
    }

    private ParsedIntent topIntent() {
        ParsedIntent intent = new ParsedIntent();
        intent.setIntent(IntentType.TOP_SCORES);
        intent.setLimit(30);
        return intent;
    }

    private ScoreRecord scoreRecord(Long id, Long chartId, Long songId, String achievement, LocalDateTime playedAt) {
        ScoreRecord record = new ScoreRecord();
        record.setId(id);
        record.setUserId(999L);
        record.setDifficultyId(chartId);
        record.setSongId(songId);
        record.setAchievementRate(achievement == null ? null : new BigDecimal(achievement));
        record.setLastPlayTime(playedAt);
        return record;
    }

    private SongDifficulty chart(Long id, Long songId) {
        SongDifficulty chart = new SongDifficulty();
        chart.setId(id);
        chart.setSongId(songId);
        chart.setDifficulty(3);
        chart.setLevelDecimal(new BigDecimal("13.4"));
        return chart;
    }

    private Song song(Long id) {
        Song song = new Song();
        song.setId(id);
        song.setSongId("mvp001");
        song.setTitle("Demo Song");
        return song;
    }
}
