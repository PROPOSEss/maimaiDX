package com.maimai.maidx.controller;

import com.maimai.maidx.dto.Result;
import com.maimai.maidx.dto.VoteRequest;
import com.maimai.maidx.entity.SongDifficultyVote;
import com.maimai.maidx.service.SongDifficultyVoteService;
import com.maimai.maidx.service.SongDifficultyVoteService.TagVoteStat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 投票控制器 - 社区标签投票
 */
@Tag(name = "社区投票", description = "谱面标签社区投票与统计")
@RestController
@RequestMapping("/vote")
@RequiredArgsConstructor
public class VoteController {

    private final SongDifficultyVoteService voteService;

    @Operation(summary = "提交谱面标签投票（最多3个标签）")
    @PostMapping("/submit")
    public Result<String> submitVote(@RequestHeader("X-User-Id") Long userId,
                                   @Valid @RequestBody VoteRequest request) {
        voteService.submitVote(userId, request.getDifficultyId(), request.getTagNames());
        return Result.success("投票成功", null);
    }

    @Operation(summary = "获取我对某谱面的投票")
    @GetMapping("/my/{difficultyId}")
    public Result<List<String>> getMyVotes(@RequestHeader("X-User-Id") Long userId,
                                           @PathVariable Long difficultyId) {
        List<SongDifficultyVote> votes = voteService.getUserVotes(userId, difficultyId);
        List<String> tagNames = votes.stream()
                .map(SongDifficultyVote::getTagName)
                .collect(Collectors.toList());
        return Result.success(tagNames);
    }

    @Operation(summary = "获取某谱面的投票统计")
    @GetMapping("/stats/{difficultyId}")
    public Result<List<TagVoteStat>> getVoteStats(@PathVariable Long difficultyId) {
        List<TagVoteStat> stats = voteService.getVoteStats(difficultyId);
        return Result.success(stats);
    }

    @Operation(summary = "获取所有可用标签列表")
    @GetMapping("/tags")
    public Result<List<Map<String, String>>> getAvailableTags() {
        List<Map<String, String>> tags = List.of(
                createTag("反手", "cross_hand"),
                createTag("交互", "alternate"),
                createTag("撞手", "clash"),
                createTag("纵连", "vertical"),
                createTag("体力", "stamina"),
                createTag("读谱", "reading"),
                createTag("节奏难", "rhythm"),
                createTag("错位星星", "star_slide"),
                createTag("Touch圈", "touch")
        );
        return Result.success(tags);
    }

    private Map<String, String> createTag(String name, String code) {
        Map<String, String> tag = new HashMap<>();
        tag.put("name", name);
        tag.put("code", code);
        return tag;
    }
}
