package com.maimai.maidx.service;

import com.maimai.maidx.dto.MvpDtos;

import java.io.IOException;
import java.util.List;

public interface MvpService {

    int syncSongs(List<MvpDtos.SongImportItem> items) throws IOException;

    List<MvpDtos.SongQueryItem> querySongs(String keyword, Integer level, java.math.BigDecimal minDs,
                                           java.math.BigDecimal maxDs, String version, int page, int size);

    List<MvpDtos.ChartItem> getCharts(String songId);

    MvpDtos.ImportResult importScores(Long userId, MvpDtos.ScoreImportRequest request);

    MvpDtos.ImportResult importScores(Long userId, MvpDtos.ScoreImportRequest request, String requestId);

    MvpDtos.B50Response getB50(Long userId, Long snapshotId);

    MvpDtos.RecommendationResponse generateRecommendations(Long userId, Long snapshotId);

    MvpDtos.RecommendationResponse getRecommendations(Long userId, Long snapshotId);

    MvpDtos.GrowthResponse getGrowth(Long userId, Long fromSnapshotId, Long toSnapshotId);

    MvpDtos.ReportResponse getReport(Long userId, Long snapshotId);
}
