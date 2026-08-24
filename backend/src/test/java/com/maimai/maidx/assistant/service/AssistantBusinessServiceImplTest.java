package com.maimai.maidx.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.service.SongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantBusinessServiceImplTest {

    @Mock private ScoreRecordRepository scoreRecordRepository;
    @Mock private SongDifficultyRepository songDifficultyRepository;
    @Mock private SongService songService;

    private AssistantBusinessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AssistantBusinessServiceImpl(
                scoreRecordRepository, songDifficultyRepository, songService);
    }

    @Test
    void recentScoresLoadsRelatedChartsAndSongs() {
        ScoreRecord record = score(1L, 11L, "99.500", 280);
        record.setSongId(21L);
        SongDifficulty chart = chart(11L, 21L, "13.8");
        Song song = song(21L, "Demo Song");
        when(scoreRecordRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(record));
        when(songDifficultyRepository.selectBatchIds(List.of(11L))).thenReturn(List.of(chart));
        when(songService.listByIds(List.of(21L))).thenReturn(List.of(song));

        AssistantBusinessService.ScoreBundle result = service.getRecentScores(7L, 5);

        assertThat(result.records()).containsExactly(record);
        assertThat(result.charts()).containsEntry(11L, chart);
        assertThat(result.songs()).containsEntry(21L, song);
    }

    @Test
    void topScoresDeduplicatesChartsAndSortsByAchievement() {
        ScoreRecord oldLower = score(1L, 11L, "99.000", 250);
        ScoreRecord newerHigher = score(2L, 11L, "100.000", 270);
        ScoreRecord other = score(3L, 12L, "99.800", 260);
        when(scoreRecordRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(oldLower, newerHigher, other));
        when(songDifficultyRepository.selectBatchIds(any())).thenReturn(List.of());

        AssistantBusinessService.ScoreBundle result = service.getTopScores(7L, 10);

        assertThat(result.records()).containsExactly(newerHigher, other);
    }

    @Test
    void randomRecommendationReturnsDifferentSongs() {
        SongDifficulty first = chart(11L, 21L, "13.0");
        SongDifficulty sameSong = chart(12L, 21L, "13.1");
        SongDifficulty second = chart(13L, 22L, "13.2");
        when(songDifficultyRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(first, sameSong, second));
        when(songService.listByIds(any())).thenReturn(List.of(song(21L, "A"), song(22L, "B")));

        AssistantBusinessService.RecommendationBundle result =
                service.getRandomRecommendations(new BigDecimal("13.0"), new BigDecimal("13.5"), 5);

        assertThat(result.charts()).extracting(SongDifficulty::getSongId)
                .containsExactlyInAnyOrder(21L, 22L);
    }

    @Test
    void trainingAdviceIsDeterministicAndKeepsEvidenceInBackend() {
        ScoreRecord first = score(1L, 11L, "99.500", 280);
        ScoreRecord second = score(2L, 12L, "98.500", 260);
        SongDifficulty firstChart = chart(11L, 21L, "13.0");
        SongDifficulty secondChart = chart(12L, 22L, "13.8");
        when(scoreRecordRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(first, second), List.of(first, second));
        when(songDifficultyRepository.selectBatchIds(any()))
                .thenReturn(List.of(firstChart, secondChart));
        when(songService.listByIds(any())).thenReturn(List.of(song(21L, "A"), song(22L, "B")));

        AssistantBusinessService.TrainingAdviceBundle result = service.getTrainingAdvice(7L, 3);

        assertThat(result.suggestions()).hasSize(3);
        assertThat(result.evidence().records()).containsExactly(first, second);
        assertThat(result.source()).isEqualTo(com.maimai.maidx.assistant.enums.AdviceSource.RULE);
    }

    @Test
    void songSearchDelegatesToSongServiceWithBoundedPage() {
        Page<Song> page = new Page<>(1, 10);
        page.setRecords(List.of(song(21L, "PANDORA PARADOXXX")));
        when(songService.searchSongs("PANDORA", 1, 10)).thenReturn(page);

        List<Song> result = service.searchSongs("PANDORA", 10);

        assertThat(result).extracting(Song::getTitle).containsExactly("PANDORA PARADOXXX");
        verify(songService).searchSongs("PANDORA", 1, 10);
    }

    private ScoreRecord score(Long id, Long chartId, String achievement, int ra) {
        ScoreRecord record = new ScoreRecord();
        record.setId(id);
        record.setDifficultyId(chartId);
        record.setAchievementRate(new BigDecimal(achievement));
        record.setRa(ra);
        return record;
    }

    private SongDifficulty chart(Long id, Long songId, String constant) {
        SongDifficulty chart = new SongDifficulty();
        chart.setId(id);
        chart.setSongId(songId);
        chart.setDifficulty(3);
        chart.setLevelDecimal(new BigDecimal(constant));
        return chart;
    }

    private Song song(Long id, String title) {
        Song song = new Song();
        song.setId(id);
        song.setSongId(String.valueOf(id));
        song.setTitle(title);
        return song;
    }
}
