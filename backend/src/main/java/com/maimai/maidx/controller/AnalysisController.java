package com.maimai.maidx.controller;

import com.maimai.maidx.dto.AbilityResponse;
import com.maimai.maidx.dto.Result;
import com.maimai.maidx.entity.PlayerAbility;
import com.maimai.maidx.entity.PlayerBind;
import com.maimai.maidx.enums.TagEnum;
import com.maimai.maidx.service.PlayerAbilityService;
import com.maimai.maidx.service.PlayerBindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分析控制器 - 能力画像与雷达图（核心API）
 */
@Tag(name = "能力分析", description = "玩家能力画像、雷达图与训练建议")
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final PlayerAbilityService playerAbilityService;
    private final PlayerBindService playerBindService;

    @Operation(summary = "获取玩家能力画像（含雷达图数据）")
    @GetMapping("/ability")
    public Result<AbilityResponse> getAbility(@RequestHeader("X-User-Id") Long userId) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            return Result.error("请先绑定MAID");
        }

        // 获取雷达图数据（包含所有9个标签维度）
        List<PlayerAbility> abilities = playerAbilityService.getRadarData(bind.getId());
        List<PlayerAbility> weaknesses = playerAbilityService.getPlayerWeaknesses(bind.getId());

        AbilityResponse response = new AbilityResponse();
        response.setPlayerId(bind.getId());
        response.setPlayerName(bind.getPlayerName());
        response.setRating(bind.getRating());

        // 转换能力数据
        response.setAbilities(abilities.stream()
                .map(this::convertTagAbility)
                .collect(Collectors.toList()));

        // 转换弱点数据
        response.setWeaknesses(weaknesses.stream()
                .map(this::convertTagAbility)
                .collect(Collectors.toList()));

        return Result.success(response);
    }

    @Operation(summary = "仅获取雷达图数据")
    @GetMapping("/radar")
    public Result<List<AbilityResponse.TagAbility>> getRadarData(
            @RequestHeader("X-User-Id") Long userId) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            return Result.error("请先绑定MAID");
        }

        List<PlayerAbility> abilities = playerAbilityService.getRadarData(bind.getId());
        List<AbilityResponse.TagAbility> radarData = abilities.stream()
                .map(this::convertTagAbility)
                .collect(Collectors.toList());

        return Result.success(radarData);
    }

    @Operation(summary = "获取玩家弱点列表")
    @GetMapping("/weaknesses")
    public Result<List<AbilityResponse.TagAbility>> getWeaknesses(
            @RequestHeader("X-User-Id") Long userId) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            return Result.error("请先绑定MAID");
        }

        List<PlayerAbility> weaknesses = playerAbilityService.getPlayerWeaknesses(bind.getId());
        List<AbilityResponse.TagAbility> result = weaknesses.stream()
                .map(this::convertTagAbility)
                .collect(Collectors.toList());

        return Result.success(result);
    }

    @Operation(summary = "刷新能力画像（重新计算）")
    @PostMapping("/ability/refresh")
    public Result<AbilityResponse> refreshAbility(@RequestHeader("X-User-Id") Long userId) {
        PlayerBind bind = playerBindService.getByUserId(userId);
        if (bind == null) {
            return Result.error("请先绑定MAID");
        }

        playerAbilityService.calculateAbility(bind.getId());

        List<PlayerAbility> abilities = playerAbilityService.getRadarData(bind.getId());
        List<PlayerAbility> weaknesses = playerAbilityService.getPlayerWeaknesses(bind.getId());

        AbilityResponse response = new AbilityResponse();
        response.setPlayerId(bind.getId());
        response.setPlayerName(bind.getPlayerName());
        response.setRating(bind.getRating());
        response.setAbilities(abilities.stream().map(this::convertTagAbility).collect(Collectors.toList()));
        response.setWeaknesses(weaknesses.stream().map(this::convertTagAbility).collect(Collectors.toList()));

        return Result.success("能力画像已更新", response);
    }

    private AbilityResponse.TagAbility convertTagAbility(PlayerAbility ability) {
        AbilityResponse.TagAbility tag = new AbilityResponse.TagAbility();
        tag.setTagName(ability.getTagName());
        tag.setAvgScore(ability.getAvgScore());
        tag.setAvgRating(ability.getAvgRating());
        tag.setTotalSongs(ability.getTotalSongs());
        tag.setSsspCount(ability.getSsspCount());
        tag.setWeaknessScore(ability.getWeaknessScore());
        tag.setIsWeakness(ability.getIsWeakness() == 1);

        // 补充标签编码和描述
        try {
            TagEnum tagEnum = TagEnum.fromName(ability.getTagName());
            tag.setTagCode(tagEnum.getCode());
            tag.setDescription(tagEnum.getDescription());
        } catch (IllegalArgumentException ignored) {
            tag.setTagCode(ability.getTagName());
            tag.setDescription("");
        }

        return tag;
    }
}
