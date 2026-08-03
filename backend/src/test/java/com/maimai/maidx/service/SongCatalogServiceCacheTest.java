package com.maimai.maidx.service;

import com.maimai.maidx.config.CacheConfig;
import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.dto.SongDetailResponse;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.repository.SongRepository;
import com.maimai.maidx.service.impl.SongCatalogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = SongCatalogServiceCacheTest.TestConfig.class)
class SongCatalogServiceCacheTest {

    @jakarta.annotation.Resource
    private SongCatalogService songCatalogService;

    @jakarta.annotation.Resource
    private SongRepository songRepository;

    @jakarta.annotation.Resource
    private SongDifficultyRepository songDifficultyRepository;

    @jakarta.annotation.Resource
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(songRepository, songDifficultyRepository);
        clear(CacheConfig.SONG_DETAIL_CACHE);
        clear(CacheConfig.SONG_CHARTS_CACHE);
    }

    @Test
    void songDetailIsLoadedFromRepositoryOnlyOnFirstQuery() {
        when(songRepository.selectOne(any())).thenReturn(song(1L, "s001", "中文歌曲"));

        SongDetailResponse first = songCatalogService.getSongDetail("s001");
        SongDetailResponse second = songCatalogService.getSongDetail("s001");

        assertThat(first.getTitle()).isEqualTo("中文歌曲");
        assertThat(second.getSongId()).isEqualTo("s001");
        verify(songRepository, times(1)).selectOne(any());
    }

    @Test
    void differentSongIdsUseDifferentCacheKeys() {
        when(songRepository.selectOne(any()))
                .thenReturn(song(1L, "s001", "Song One"))
                .thenReturn(song(2L, "s002", "Song Two"));

        songCatalogService.getSongDetail("s001");
        songCatalogService.getSongDetail("s002");

        verify(songRepository, times(2)).selectOne(any());
    }

    @Test
    void chartsAreLoadedFromRepositoryOnlyOnFirstQuery() {
        when(songRepository.selectOne(any())).thenReturn(song(1L, "s001", "Song One"));
        when(songDifficultyRepository.selectList(any())).thenReturn(List.of(chart(11L, 1L, 3)));

        List<MvpDtos.ChartItem> first = songCatalogService.getCharts("s001");
        List<MvpDtos.ChartItem> second = songCatalogService.getCharts("s001");

        assertThat(first).hasSize(1);
        assertThat(second.get(0).getDs()).isEqualByComparingTo("13.7");
        verify(songRepository, times(1)).selectOne(any());
        verify(songDifficultyRepository, times(1)).selectList(any());
    }

    @Test
    void songDetailAndChartsUseDifferentCacheRegions() {
        when(songRepository.selectOne(any())).thenReturn(song(1L, "s001", "Song One"));
        when(songDifficultyRepository.selectList(any())).thenReturn(List.of(chart(11L, 1L, 3)));

        songCatalogService.getSongDetail("s001");
        songCatalogService.getCharts("s001");
        songCatalogService.getSongDetail("s001");
        songCatalogService.getCharts("s001");

        verify(songRepository, times(2)).selectOne(any());
        verify(songDifficultyRepository, times(1)).selectList(any());
    }

    @Test
    void clearSongCachesClearsBothCacheRegions() {
        when(songRepository.selectOne(any())).thenReturn(song(1L, "s001", "Song One"));
        when(songDifficultyRepository.selectList(any())).thenReturn(List.of(chart(11L, 1L, 3)));

        songCatalogService.getSongDetail("s001");
        songCatalogService.getCharts("s001");
        songCatalogService.clearSongCaches();
        songCatalogService.getSongDetail("s001");
        songCatalogService.getCharts("s001");

        verify(songRepository, times(4)).selectOne(any());
        verify(songDifficultyRepository, times(2)).selectList(any());
    }

    private void clear(String cacheName) {
        if (cacheManager.getCache(cacheName) != null) {
            cacheManager.getCache(cacheName).clear();
        }
    }

    private static Song song(Long id, String publicSongId, String title) {
        Song song = new Song();
        song.setId(id);
        song.setSongId(publicSongId);
        song.setTitle(title);
        song.setArtist("Artist");
        song.setBpm(180);
        song.setVersion("UNiVERSE");
        song.setGenre("POPS");
        return song;
    }

    private static SongDifficulty chart(Long id, Long songId, Integer difficulty) {
        SongDifficulty chart = new SongDifficulty();
        chart.setId(id);
        chart.setSongId(songId);
        chart.setDifficulty(difficulty);
        chart.setLevel(13);
        chart.setLevelDecimal(new BigDecimal("13.7"));
        chart.setFitDiff(new BigDecimal("13.4"));
        chart.setNoteCount(700);
        chart.setTapCount(400);
        chart.setHoldCount(80);
        chart.setSlideCount(120);
        chart.setTouchCount(60);
        chart.setBreakCount(40);
        chart.setCharter("Tester");
        return chart;
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        SongRepository songRepository() {
            return mock(SongRepository.class);
        }

        @Bean
        SongDifficultyRepository songDifficultyRepository() {
            return mock(SongDifficultyRepository.class);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.SONG_DETAIL_CACHE, CacheConfig.SONG_CHARTS_CACHE);
        }

        @Bean
        SongCatalogService songCatalogService(SongRepository songRepository,
                                              SongDifficultyRepository songDifficultyRepository,
                                              CacheManager cacheManager) {
            return new SongCatalogServiceImpl(songRepository, songDifficultyRepository, cacheManager);
        }
    }
}
