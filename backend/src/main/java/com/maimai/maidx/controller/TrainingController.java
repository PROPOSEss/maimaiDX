package com.maimai.maidx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maimai.maidx.dto.Result;
import com.maimai.maidx.dto.TrainingResponse;
import com.maimai.maidx.entity.*;
import com.maimai.maidx.enums.DifficultyEnum;
import com.maimai.maidx.enums.TagEnum;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.repository.SongRepository;
import com.maimai.maidx.service.PlayerBindService;
import com.maimai.maidx.service.TrainingSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 训练控制器 - 训练建议与推荐谱面
 */
@Tag(name = "训练建议", description = "个性化训练建议与推荐谱面")
@RestController
@RequestMapping("/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingSuggestionService trainingSuggestionService;
    private final PlayerBindService playerBindService;
    private final SongRepository songRepository;
    private final SongDifficultyRepository songDifficultyRepository;

    @Operation(summary = "获取训练建议列表")
    @GetMapping("/suggestions")
    public Result<List<TrainingResponse>> getSuggestions(@RequestHeader("X-User-Id") Long userId) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            return Result.error("请先绑定MAID");
        }

        List<TrainingSuggestion> suggestions = trainingSuggestionService.getPlayerSuggestions(bind.getId());
        List<TrainingResponse> responses = suggestions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return Result.success(responses);
    }

    @Operation(summary = "刷新训练建议（重新生成）")
    @PostMapping("/suggestions/refresh")
    public Result<List<TrainingResponse>> refreshSuggestions(@RequestHeader("X-User-Id") Long userId) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            return Result.error("请先绑定MAID");
        }

        List<TrainingSuggestion> suggestions = trainingSuggestionService.refreshSuggestions(bind.getId());
        List<TrainingResponse> responses = suggestions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return Result.success("训练建议已刷新", responses);
    }

    @Operation(summary = "获取针对某个弱点的训练建议")
    @GetMapping("/suggestions/{tagName}")
    public Result<List<TrainingResponse>> getSuggestionsByTag(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String tagName) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            return Result.error("请先绑定MAID");
        }

        List<TrainingSuggestion> all = trainingSuggestionService.getPlayerSuggestions(bind.getId());
        List<TrainingResponse> filtered = all.stream()
                .filter(s -> s.getTagName().equals(tagName))
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return Result.success(filtered);
    }

    private TrainingResponse convertToResponse(TrainingSuggestion suggestion) {
        TrainingResponse resp = new TrainingResponse();
        resp.setId(suggestion.getId());
        resp.setTagName(suggestion.getTagName());
        resp.setTagNameDisplay(suggestion.getTagName());
        resp.setDifficultyId(suggestion.getDifficultyId());
        resp.setPriority(suggestion.getPriority());
        resp.setReason(suggestion.getReason());

        // 关联谱面和歌曲信息
        SongDifficulty diff = songDifficultyRepository.selectById(suggestion.getDifficultyId());
        if (diff != null) {
            resp.setLevel(diff.getLevel());
            try {
                resp.setDifficultyDisplay(DifficultyEnum.fromCode(diff.getDifficulty()).getName());
            } catch (Exception ignored) {
                resp.setDifficultyDisplay("UNKNOWN");
            }

            Song song = songRepository.selectById(diff.getSongId());
            if (song != null) {
                TrainingResponse.SongInfo songInfo = new TrainingResponse.SongInfo();
                songInfo.setSongId(song.getSongId());
                songInfo.setTitle(song.getTitle());
                songInfo.setArtist(song.getArtist());
                resp.setSong(songInfo);
            }
        }

        return resp;
    }
}
