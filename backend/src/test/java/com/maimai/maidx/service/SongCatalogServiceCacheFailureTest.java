package com.maimai.maidx.service;

import com.maimai.maidx.config.CacheConfig;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.repository.SongRepository;
import com.maimai.maidx.service.impl.SongCatalogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = SongCatalogServiceCacheFailureTest.TestConfig.class)
class SongCatalogServiceCacheFailureTest {

    @jakarta.annotation.Resource
    private SongCatalogService songCatalogService;

    @jakarta.annotation.Resource
    private SongRepository songRepository;

    @jakarta.annotation.Resource
    private FaultyCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(songRepository);
        cacheManager.reset();
    }

    @Test
    void cacheGetFailureFallsBackToRepository() {
        cacheManager.setFailGet(true);
        when(songRepository.selectOne(any())).thenReturn(song());

        assertThat(songCatalogService.getSongDetail("s001").getTitle()).isEqualTo("Song One");

        verify(songRepository, times(1)).selectOne(any());
    }

    @Test
    void cachePutFailureStillReturnsRepositoryResult() {
        cacheManager.setFailPut(true);
        when(songRepository.selectOne(any())).thenReturn(song());

        assertThat(songCatalogService.getSongDetail("s001").getSongId()).isEqualTo("s001");

        verify(songRepository, times(1)).selectOne(any());
    }

    @Test
    void cacheClearFailureDoesNotThrow() {
        cacheManager.setFailClear(true);

        assertThatCode(() -> songCatalogService.clearSongCaches()).doesNotThrowAnyException();
    }

    private static Song song() {
        Song song = new Song();
        song.setId(1L);
        song.setSongId("s001");
        song.setTitle("Song One");
        return song;
    }

    @Configuration
    @EnableCaching
    static class TestConfig implements CachingConfigurer {

        private final FaultyCacheManager cacheManager = new FaultyCacheManager();

        @Bean
        SongRepository songRepository() {
            return mock(SongRepository.class);
        }

        @Bean
        SongDifficultyRepository songDifficultyRepository() {
            return mock(SongDifficultyRepository.class);
        }

        @Bean
        SongCatalogService songCatalogService(SongRepository songRepository,
                                              SongDifficultyRepository songDifficultyRepository,
                                              CacheManager cacheManager) {
            return new SongCatalogServiceImpl(songRepository, songDifficultyRepository, cacheManager);
        }

        @Bean
        @Override
        public FaultyCacheManager cacheManager() {
            return cacheManager;
        }

        @Bean
        @Override
        public CacheErrorHandler errorHandler() {
            return new CacheConfig().errorHandler();
        }
    }

    static class FaultyCacheManager implements CacheManager {

        private final FaultyCache detail = new FaultyCache(CacheConfig.SONG_DETAIL_CACHE);
        private final FaultyCache charts = new FaultyCache(CacheConfig.SONG_CHARTS_CACHE);

        void setFailGet(boolean failGet) {
            detail.failGet = failGet;
            charts.failGet = failGet;
        }

        void setFailPut(boolean failPut) {
            detail.failPut = failPut;
            charts.failPut = failPut;
        }

        void setFailClear(boolean failClear) {
            detail.failClear = failClear;
            charts.failClear = failClear;
        }

        void reset() {
            setFailGet(false);
            setFailPut(false);
            setFailClear(false);
            detail.clearStore();
            charts.clearStore();
        }

        @Override
        public Cache getCache(String name) {
            if (CacheConfig.SONG_DETAIL_CACHE.equals(name)) {
                return detail;
            }
            if (CacheConfig.SONG_CHARTS_CACHE.equals(name)) {
                return charts;
            }
            return null;
        }

        @Override
        public Collection<String> getCacheNames() {
            return List.of(CacheConfig.SONG_DETAIL_CACHE, CacheConfig.SONG_CHARTS_CACHE);
        }
    }

    static class FaultyCache extends ConcurrentMapCache {

        private boolean failGet;
        private boolean failPut;
        private boolean failClear;

        FaultyCache(String name) {
            super(name);
        }

        @Override
        public ValueWrapper get(Object key) {
            if (failGet) {
                throw new IllegalStateException("cache get failed");
            }
            return super.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            if (failGet) {
                throw new IllegalStateException("cache get failed");
            }
            return super.get(key, type);
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            if (failGet) {
                throw new IllegalStateException("cache get failed");
            }
            return super.get(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            if (failPut) {
                throw new IllegalStateException("cache put failed");
            }
            super.put(key, value);
        }

        @Override
        public void clear() {
            if (failClear) {
                throw new IllegalStateException("cache clear failed");
            }
            super.clear();
        }

        void clearStore() {
            super.clear();
        }
    }
}
