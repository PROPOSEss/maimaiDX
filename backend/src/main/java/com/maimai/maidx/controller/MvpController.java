package com.maimai.maidx.controller;

import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.dto.Result;
import com.maimai.maidx.service.MvpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "MVP Demo", description = "Song sync, score import, B50, recommendations and report")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class MvpController {

    private final MvpService mvpService;

    @Operation(summary = "Query songs")
    @GetMapping("/songs")
    public Result<List<MvpDtos.SongQueryItem>> querySongs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) BigDecimal minDs,
            @RequestParam(required = false) BigDecimal maxDs,
            @RequestParam(required = false) String version,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(mvpService.querySongs(keyword, level, minDs, maxDs, version, page, size));
    }

    @Operation(summary = "Sync default local song library")
    @PostMapping("/songs/sync")
    public Result<String> syncSongs() throws Exception {
        int chartCount = mvpService.syncSongs(null);
        return Result.success("songs synced", "synced charts: " + chartCount);
    }

    @Operation(summary = "Sync custom song library from JSON request body")
    @PostMapping("/songs/sync/custom")
    public Result<String> syncSongs(@RequestBody List<MvpDtos.SongImportItem> items) throws Exception {
        int chartCount = mvpService.syncSongs(items);
        return Result.success("songs synced", "synced charts: " + chartCount);
    }

    @Operation(summary = "Get charts by song id")
    @GetMapping("/charts/{songId}")
    public Result<List<MvpDtos.ChartItem>> getCharts(@PathVariable String songId) {
        return Result.success(mvpService.getCharts(songId));
    }

    @Operation(summary = "Import player scores from JSON and create a score snapshot")
    @PostMapping("/player/import")
    public Result<MvpDtos.ImportResult> importScores(
            @RequestParam Long userId,
            @RequestBody MvpDtos.ScoreImportRequest request) {
        return Result.success(mvpService.importScores(userId, request));
    }

    @Operation(summary = "Get player B50 analysis")
    @GetMapping("/player/{userId}/b50")
    public Result<MvpDtos.B50Response> getB50(
            @PathVariable Long userId,
            @RequestParam(required = false) Long snapshotId) {
        return Result.success(mvpService.getB50(userId, snapshotId));
    }

    @Operation(summary = "Generate song recommendations")
    @PostMapping("/player/{userId}/recommend")
    public Result<MvpDtos.RecommendationResponse> generateRecommendations(
            @PathVariable Long userId,
            @RequestParam(required = false) Long snapshotId) {
        return Result.success(mvpService.generateRecommendations(userId, snapshotId));
    }

    @Operation(summary = "Get saved recommendations")
    @GetMapping("/player/{userId}/recommendations")
    public Result<MvpDtos.RecommendationResponse> getRecommendations(
            @PathVariable Long userId,
            @RequestParam(required = false) Long snapshotId) {
        return Result.success(mvpService.getRecommendations(userId, snapshotId));
    }

    @Operation(summary = "Compare player growth between two score snapshots")
    @GetMapping("/player/{userId}/growth")
    public Result<MvpDtos.GrowthResponse> getGrowth(
            @PathVariable Long userId,
            @RequestParam(required = false) Long fromSnapshotId,
            @RequestParam(required = false) Long toSnapshotId) {
        return Result.success(mvpService.getGrowth(userId, fromSnapshotId, toSnapshotId));
    }

    @Operation(summary = "Get growth report")
    @GetMapping("/player/{userId}/report")
    public Result<MvpDtos.ReportResponse> getReport(
            @PathVariable Long userId,
            @RequestParam(required = false) Long snapshotId) {
        return Result.success(mvpService.getReport(userId, snapshotId));
    }
}
