package com.maimai.maidx.controller;

import com.maimai.maidx.config.GlobalExceptionHandler;
import com.maimai.maidx.dto.BindRequest;
import com.maimai.maidx.dto.Result;
import com.maimai.maidx.entity.PlayerBind;
import com.maimai.maidx.service.PlayerBindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 玩家控制器 - MAID绑定与成绩同步
 */
@Tag(name = "玩家管理", description = "MAID绑定、解绑与成绩同步")
@RestController
@RequestMapping("/player")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class PlayerController {

    private final PlayerBindService playerBindService;

    @Operation(summary = "绑定MAID")
    @PostMapping("/bind")
    public Result<PlayerBind> bindMaId(@RequestHeader("X-User-Id") Long userId,
                                       @Valid @RequestBody BindRequest request) {
        PlayerBind bind = playerBindService.bindMaId(userId, request.getMaId());
        return Result.success(bind);
    }

    @Operation(summary = "解绑MAID")
    @DeleteMapping("/unbind")
    public Result<Void> unbindMaId(@RequestHeader("X-User-Id") Long userId) {
        playerBindService.unbindMaId(userId);
        return Result.success();
    }

    @Operation(summary = "获取绑定信息")
    @GetMapping("/info")
    public Result<PlayerBind> getBindInfo(@RequestHeader("X-User-Id") Long userId) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        return Result.success(bind);
    }

    @Operation(summary = "同步玩家成绩")
    @PostMapping("/sync")
    public Result<Void> syncScores(@RequestHeader("X-User-Id") Long userId) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            throw new GlobalExceptionHandler.BusinessException("请先绑定MAID");
        }
        boolean success = playerBindService.syncScores(bind.getId());
        if (success) {
            return Result.success("成绩同步成功", null);
        } else {
            throw new GlobalExceptionHandler.BusinessException("成绩同步失败，请稍后重试");
        }
    }
}
