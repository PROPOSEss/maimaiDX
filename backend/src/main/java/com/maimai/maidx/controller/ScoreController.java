package com.maimai.maidx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maimai.maidx.dto.Result;
import com.maimai.maidx.dto.ScoreResponse;
import com.maimai.maidx.entity.PlayerBind;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.enums.DifficultyEnum;
import com.maimai.maidx.service.PlayerBindService;
import com.maimai.maidx.service.ScoreRecordService;
import com.maimai.maidx.service.SongService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maimai.maidx.repository.SongRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成绩控制器 - 玩家成绩查询
 */
@Tag(name = "成绩管理", description = "玩家成绩查询（B50、全部成绩等）")
@RestController
@RequestMapping("/score")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreRecordService scoreRecordService;
    private final PlayerBindService playerBindService;
    private final SongRepository songRepository;
    private final SongDifficultyRepository songDifficultyRepository;

    @Operation(summary = "获取玩家B50成绩")
    @GetMapping("/b50")
    public Result<List<ScoreResponse>> getB50(@RequestHeader("X-User-Id") Long userId) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            return Result.error("请先绑定MAID");
        }

        List<ScoreRecord> records = scoreRecordService.getPlayerB50(bind.getId());
        List<ScoreResponse> responses = records.stream()
                .map(r -> convertToResponse(r))
                .collect(Collectors.toList());

        return Result.success(responses);
    }

    @Operation(summary = "分页获取玩家全部成绩")
    @GetMapping("/list")
    public Result<Page<ScoreResponse>> getScores(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            return Result.error("请先绑定MAID");
        }

        Page<ScoreRecord> recordPage = scoreRecordService.getPlayerScores(bind.getId(), page, size);
        Page<ScoreResponse> responsePage = new Page<>(recordPage.getCurrent(), recordPage.getSize(), recordPage.getTotal());
        responsePage.setRecords(recordPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));

        return Result.success(responsePage);
    }

    private ScoreResponse convertToResponse(ScoreRecord record) {
        ScoreResponse resp = new ScoreResponse();
        resp.setId(record.getId());
        resp.setDifficultyId(record.getDifficultyId());
        resp.setScore(record.getScore());
        resp.setRank(record.getRank());
        resp.setFc(record.getFc());
        resp.setFs(record.getFs());
        resp.setPlayCount(record.getPlayCount());
        resp.setBestPlayTime(record.getBestPlayTime() != null ? record.getBestPlayTime().toString() : null);

        // 关联歌曲信息
        SongDifficulty diff = songDifficultyRepository.selectById(record.getDifficultyId());
        if (diff != null) {
            resp.setDifficulty(diff.getDifficulty());
            try {
                resp.setDifficultyName(DifficultyEnum.fromCode(diff.getDifficulty()).getName());
            } catch (Exception ignored) {
                resp.setDifficultyName("UNKNOWN");
            }
            resp.setLevel(diff.getLevel());

            Song song = songRepository.selectById(diff.getSongId());
            if (song != null) {
                resp.setSongId(song.getSongId());
                resp.setTitle(song.getTitle());
                resp.setArtist(song.getArtist());
            }
        }

        return resp;
    }
}
