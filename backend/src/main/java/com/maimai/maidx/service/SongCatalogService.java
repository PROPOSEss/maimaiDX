package com.maimai.maidx.service;

import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.dto.SongDetailResponse;

import java.util.List;

public interface SongCatalogService {

    SongDetailResponse getSongDetail(String songId);

    List<MvpDtos.ChartItem> getCharts(String songId);

    void clearSongCaches();
}
