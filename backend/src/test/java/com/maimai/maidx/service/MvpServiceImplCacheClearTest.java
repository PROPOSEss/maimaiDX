package com.maimai.maidx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.repository.RecommendationItemRepository;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.ScoreSnapshotRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.repository.SongRepository;
import com.maimai.maidx.service.impl.MvpServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ResourceLoader;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MvpServiceImplCacheClearTest {

    @Mock private SongRepository songRepository;
    @Mock private SongDifficultyRepository songDifficultyRepository;
    @Mock private ScoreSnapshotRepository scoreSnapshotRepository;
    @Mock private ScoreRecordRepository scoreRecordRepository;
    @Mock private RecommendationItemRepository recommendationItemRepository;
    @Mock private ResourceLoader resourceLoader;
    @Mock private SongCatalogService songCatalogService;

    private MvpServiceImpl mvpService;

    @BeforeEach
    void setUp() {
        mvpService = new MvpServiceImpl(
                songRepository,
                songDifficultyRepository,
                scoreSnapshotRepository,
                scoreRecordRepository,
                recommendationItemRepository,
                new ObjectMapper(),
                resourceLoader,
                songCatalogService);
    }

    @Test
    void syncSongsClearsSongCachesAfterSuccessfulSync() throws Exception {
        prepareInsertMocks();

        int chartCount = mvpService.syncSongs(List.of(songImport()));

        assertThat(chartCount).isEqualTo(1);
        verify(songCatalogService).clearSongCaches();
    }

    @Test
    void syncSongsDoesNotFailWhenCacheClearFails() {
        prepareInsertMocks();
        doThrow(new IllegalStateException("redis down")).when(songCatalogService).clearSongCaches();

        assertThatCode(() -> mvpService.syncSongs(List.of(songImport()))).doesNotThrowAnyException();
    }

    private void prepareInsertMocks() {
        when(songRepository.selectOne(any())).thenReturn(null);
        when(songDifficultyRepository.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            Song song = invocation.getArgument(0);
            song.setId(1L);
            return 1;
        }).when(songRepository).insert(any());
        doAnswer(invocation -> {
            SongDifficulty chart = invocation.getArgument(0);
            chart.setId(11L);
            return 1;
        }).when(songDifficultyRepository).insert(any());
    }

    private MvpDtos.SongImportItem songImport() {
        MvpDtos.ChartImportItem chart = new MvpDtos.ChartImportItem();
        chart.setDifficulty(3);
        chart.setLevel(13);
        chart.setDs(new BigDecimal("13.7"));

        MvpDtos.SongImportItem song = new MvpDtos.SongImportItem();
        song.setSongId("s001");
        song.setTitle("Song One");
        song.setCharts(List.of(chart));
        return song;
    }
}
