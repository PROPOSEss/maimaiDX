package com.maimai.maidx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maimai.maidx.dto.Result;
import com.maimai.maidx.dto.SongDetailResponse;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.entity.SongFeature;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.enums.DifficultyEnum;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.service.*;
import com.maimai.maidx.service.SongDifficultyVoteService.TagVoteStat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 歌曲控制器 - 歌曲查询与谱面详情
 */
@Tag(name = "歌曲管理", description = "歌曲查询、搜索与谱面详情")
@RestController
@RequestMapping("/song")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class SongController {

    private final SongService songService;
    private final SongFeatureService songFeatureService;
    private final SongDifficultyVoteService voteService;
    private final SongDifficultyRepository songDifficultyRepository;
    private final ScoreRecordRepository scoreRecordRepository;
    private final PlayerBindService playerBindService;

    @Operation(summary = "分页查询歌曲列表")
    @GetMapping("/list")
    public Result<Page<Song>> listSongs(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "等级筛选") @RequestParam(required = false) Integer level,
            @Parameter(description = "难度筛选") @RequestParam(required = false) Integer difficulty) {
        Page<Song> result = songService.pageSongs(page, size, keyword, level, difficulty);
        return Result.success(result);
    }

    @Operation(summary = "搜索歌曲")
    @GetMapping("/search")
    public Result<Page<Song>> searchSongs(
            @Parameter(description = "搜索关键词") @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<Song> result = songService.searchSongs(keyword, page, size);
        return Result.success(result);
    }

    @Operation(summary = "获取歌曲详情（含谱面标签和投票数据）")
    @GetMapping("/{songId}")
    public Result<SongDetailResponse> getSongDetail(
            @PathVariable String songId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 1. 获取歌曲基本信息
        Song song = songService.getBySongId(songId);
        if (song == null) {
            return Result.error(404, "歌曲不存在");
        }

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

        // 2. 获取所有难度的谱面信息（由Service层查询填充）
        // 实际查询在service完成，这里先设置基础字段
        // 延迟到具体的SongService方法中实现

        List<SongDifficulty> difficulties = songDifficultyRepository.selectList(
                new LambdaQueryWrapper<SongDifficulty>()
                        .eq(SongDifficulty::getSongId, song.getId())
                        .orderByAsc(SongDifficulty::getDifficulty));

        Long playerId = null;
        if (userId != null) {
            com.maimai.maidx.entity.PlayerBind bind = playerBindService.getByUserId(userId);
            playerId = bind != null ? bind.getId() : null;
        }

        final Long currentPlayerId = playerId;
        response.setDifficulties(difficulties.stream()
                .map(difficulty -> convertDifficulty(difficulty, currentPlayerId))
                .collect(Collectors.toList()));

        List<String> votedTags = new ArrayList<>();
        Map<String, SongDetailResponse.VoteStat> voteStatMap = new java.util.LinkedHashMap<>();
        for (SongDifficulty difficulty : difficulties) {
            if (userId != null) {
                voteService.getUserVotes(userId, difficulty.getId()).stream()
                        .map(vote -> vote.getTagName())
                        .filter(tag -> !votedTags.contains(tag))
                        .forEach(votedTags::add);
            }

            for (TagVoteStat stat : voteService.getVoteStats(difficulty.getId())) {
                SongDetailResponse.VoteStat current = voteStatMap.computeIfAbsent(
                        stat.tagName(), key -> {
                            SongDetailResponse.VoteStat item = new SongDetailResponse.VoteStat();
                            item.setTagName(key);
                            item.setTotalWeight(0.0);
                            item.setVoteCount(0);
                            return item;
                        });
                current.setTotalWeight(current.getTotalWeight() + stat.totalWeight());
                current.setVoteCount(current.getVoteCount() + stat.voteCount());
            }
        }
        response.setVotedTags(votedTags);
        response.setVoteStats(new ArrayList<>(voteStatMap.values()));

        return Result.success(response);
    }

    private SongDetailResponse.DifficultyInfo convertDifficulty(SongDifficulty difficulty, Long playerId) {
        SongDetailResponse.DifficultyInfo info = new SongDetailResponse.DifficultyInfo();
        info.setId(difficulty.getId());
        info.setDifficulty(difficulty.getDifficulty());
        try {
            info.setDifficultyName(DifficultyEnum.fromCode(difficulty.getDifficulty()).getName());
        } catch (IllegalArgumentException ignored) {
            info.setDifficultyName("UNKNOWN");
        }
        info.setLevel(difficulty.getLevel());
        info.setLevelDecimal(difficulty.getLevelDecimal() != null
                ? difficulty.getLevelDecimal().doubleValue()
                : null);
        info.setNoteCount(difficulty.getNoteCount());
        info.setTapCount(difficulty.getTapCount());
        info.setHoldCount(difficulty.getHoldCount());
        info.setSlideCount(difficulty.getSlideCount());
        info.setTouchCount(difficulty.getTouchCount());
        info.setBreakCount(difficulty.getBreakCount());

        info.setFeatures(songFeatureService.getFeaturesByDifficultyId(difficulty.getId()).stream()
                .map(feature -> {
                    SongDetailResponse.TagInfo tag = new SongDetailResponse.TagInfo();
                    tag.setTagName(feature.getTagName());
                    tag.setWeight(feature.getWeight() != null ? feature.getWeight().doubleValue() : null);
                    tag.setSource(feature.getSource());
                    return tag;
                })
                .collect(Collectors.toList()));

        if (playerId != null) {
            ScoreRecord record = scoreRecordRepository.selectOne(
                    new LambdaQueryWrapper<ScoreRecord>()
                            .eq(ScoreRecord::getPlayerId, playerId)
                            .eq(ScoreRecord::getDifficultyId, difficulty.getId())
                            .last("LIMIT 1"));
            if (record != null) {
                SongDetailResponse.PlayerScore score = new SongDetailResponse.PlayerScore();
                score.setScore(record.getScore());
                score.setRank(record.getRank());
                score.setFc(record.getFc());
                score.setFs(record.getFs());
                score.setPlayCount(record.getPlayCount());
                info.setPlayerScore(score);
            }
        }

        return info;
    }
}
