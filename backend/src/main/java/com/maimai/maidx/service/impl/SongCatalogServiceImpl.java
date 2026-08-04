package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maimai.maidx.config.CacheConfig;
import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.dto.SongDetailResponse;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.enums.DifficultyEnum;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.repository.SongRepository;
import com.maimai.maidx.service.SongCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongCatalogServiceImpl implements SongCatalogService {

    private final SongRepository songRepository;
    private final SongDifficultyRepository songDifficultyRepository;
    private final CacheManager cacheManager;

    @Override
    @Cacheable(cacheNames = CacheConfig.SONG_DETAIL_CACHE, key = "#songId", unless = "#result == null")
    public SongDetailResponse getSongDetail(String songId) {
        return findSongByPublicId(songId)
                .map(this::toSongDetailResponse)
                .orElse(null);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.SONG_CHARTS_CACHE, key = "#songId", unless = "#result == null || #result.isEmpty()")
    public List<MvpDtos.ChartItem> getCharts(String songId) {
        return findSongByPublicId(songId)
                .map(song -> songDifficultyRepository.selectList(new LambdaQueryWrapper<SongDifficulty>()
                        .eq(SongDifficulty::getSongId, song.getId())
                        .orderByAsc(SongDifficulty::getDifficulty))
                        .stream()
                        .map(this::toChartItem)
                        .collect(Collectors.toCollection(ArrayList::new)))
                .orElseGet(ArrayList::new);
    }

    @Override
    public void clearSongCaches() {
        clearCache(CacheConfig.SONG_DETAIL_CACHE);
        clearCache(CacheConfig.SONG_CHARTS_CACHE);
    }

    private Optional<Song> findSongByPublicId(String songId) {
        return Optional.ofNullable(songRepository.selectOne(new LambdaQueryWrapper<Song>()
                .eq(Song::getSongId, songId)
                .last("LIMIT 1")));
    }

    private void clearCache(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException e) {
            log.warn("Redis cache clear failed after song sync: cache={}, error={}",
                    cacheName, e.getClass().getSimpleName());
        }
    }

    private SongDetailResponse toSongDetailResponse(Song song) {
        SongDetailResponse response = new SongDetailResponse();
        response.setId(song.getId());
        response.setSongId(song.getSongId());
        response.setTitle(song.getTitle());
        response.setTitleEn(song.getTitleEn());
        response.setArtist(song.getArtist());
        response.setArtistEn(song.getArtistEn());
        response.setBpm(song.getBpm());
        response.setVersion(song.getVersion());
        response.setGenre(song.getGenre());
        return response;
    }

    private MvpDtos.ChartItem toChartItem(SongDifficulty chart) {
        MvpDtos.ChartItem item = new MvpDtos.ChartItem();
        item.setId(chart.getId());
        item.setDifficulty(chart.getDifficulty());
        item.setDifficultyName(difficultyName(chart.getDifficulty()));
        item.setLevel(chart.getLevel());
        item.setDs(chart.getLevelDecimal());
        item.setFitDiff(chart.getFitDiff());
        item.setNotes(chart.getNoteCount());
        item.setTap(chart.getTapCount());
        item.setHold(chart.getHoldCount());
        item.setSlide(chart.getSlideCount());
        item.setTouch(chart.getTouchCount());
        item.setBreakCount(chart.getBreakCount());
        item.setCharter(chart.getCharter());
        return item;
    }

    private String difficultyName(Integer difficulty) {
        if (difficulty == null) {
            return "UNKNOWN";
        }
        try {
            return DifficultyEnum.fromCode(difficulty).getName();
        } catch (IllegalArgumentException e) {
            return "UNKNOWN";
        }
    }
}
