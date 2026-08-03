package com.maimai.maidx.service;

import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.dto.SongDetailResponse;
import com.maimai.maidx.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;

public interface SongCatalogService {

    @Cacheable(cacheManager = "cacheManager", cacheNames = CacheConfig.SONG_DETAIL_CACHE,
            key = "#songId", unless = "#result == null")
    SongDetailResponse getSongDetail(String songId);

    @Cacheable(cacheManager = "cacheManager", cacheNames = CacheConfig.SONG_CHARTS_CACHE,
            key = "#songId", unless = "#result == null || #result.isEmpty()")
    List<MvpDtos.ChartItem> getCharts(String songId);

    void clearSongCaches();
}
