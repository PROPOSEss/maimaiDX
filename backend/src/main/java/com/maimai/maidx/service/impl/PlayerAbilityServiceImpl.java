package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maimai.maidx.entity.*;
import com.maimai.maidx.enums.TagEnum;
import com.maimai.maidx.repository.PlayerAbilityRepository;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongFeatureRepository;
import com.maimai.maidx.service.PlayerAbilityService;
import com.maimai.maidx.service.SongFeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 玩家能力画像服务实现（核心算法）
 *
 * 算法流程：
 * 1. 获取玩家所有成绩记录
 * 2. 关联每条成绩的谱面标签
 * 3. 按标签聚合：计算该标签下所有谱面的平均分、平均Rating
 * 4. 计算弱点评分：与所有标签平均水平对比，低于平均水平越多越弱
 * 5. 标记弱点：弱点评分超过阈值的标签
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerAbilityServiceImpl extends ServiceImpl<PlayerAbilityRepository, PlayerAbility> implements PlayerAbilityService {

    private final ScoreRecordRepository scoreRecordRepository;
    private final SongFeatureRepository songFeatureRepository;
    private final SongFeatureService songFeatureService;

    /** 弱点判定阈值：弱点评分 > 此值视为弱点 */
    private static final double WEAKNESS_THRESHOLD = 60.0;

    @Override
    @Transactional
    public void calculateAbility(Long playerId) {
        log.info("开始计算玩家能力画像: playerId={}", playerId);

        // 1. 获取玩家所有成绩
        List<ScoreRecord> scores = scoreRecordRepository.selectList(
                new LambdaQueryWrapper<ScoreRecord>().eq(ScoreRecord::getPlayerId, playerId));

        if (scores.isEmpty()) {
            log.warn("玩家{}暂无成绩记录，跳过能力画像计算", playerId);
            return;
        }

        // 收集所有谱面ID
        List<Long> difficultyIds = scores.stream()
                .map(ScoreRecord::getDifficultyId)
                .distinct()
                .collect(Collectors.toList());

        // 2. 获取所有谱面标签
        Map<Long, List<SongFeature>> featureMap = songFeatureService.getFeaturesByDifficultyIds(difficultyIds);

        // 3. 按标签聚合成绩数据
        // tagData: tagName -> {总分, 总Rating, 谱面数, SSS+数}
        Map<String, TagAggregateData> tagDataMap = new HashMap<>();

        for (ScoreRecord score : scores) {
            List<SongFeature> features = featureMap.get(score.getDifficultyId());
            if (features == null || features.isEmpty()) {
                continue;
            }

            int scoreValue = score.getScore() != null ? score.getScore() : 0;
            // 计算该条成绩的Rating贡献（简化计算）
            double ratingContribution = scoreValue * 0.00001;

            for (SongFeature feature : features) {
                String tagName = feature.getTagName();
                double weight = feature.getWeight().doubleValue();

                TagAggregateData data = tagDataMap.computeIfAbsent(tagName, k -> new TagAggregateData());
                data.weightedScoreSum += scoreValue * (weight / 100.0);
                data.weightedRatingSum += ratingContribution * (weight / 100.0);
                data.weightCount += (weight / 100.0);
                data.totalSongs++;
                if ("SSS+".equals(score.getRank())) {
                    data.ssspCount++;
                }
            }
        }

        // 4. 计算各标签的平均值和弱点评分
        List<PlayerAbility> abilities = new ArrayList<>();
        double globalAvgScore = 0;
        int validTagCount = 0;

        for (Map.Entry<String, TagAggregateData> entry : tagDataMap.entrySet()) {
            String tagName = entry.getKey();
            TagAggregateData data = entry.getValue();

            if (data.weightCount <= 0) continue;

            double avgScore = data.weightedScoreSum / data.weightCount;
            double avgRating = data.weightedRatingSum / data.weightCount;

            globalAvgScore += avgScore;
            validTagCount++;

            PlayerAbility ability = new PlayerAbility();
            ability.setPlayerId(playerId);
            ability.setTagName(tagName);
            ability.setAvgScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP));
            ability.setAvgRating(BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP));
            ability.setTotalSongs(data.totalSongs);
            ability.setSsspCount(data.ssspCount);

            abilities.add(ability);
        }

        // 5. 计算弱点评分（与全局平均对比）
        globalAvgScore = validTagCount > 0 ? globalAvgScore / validTagCount : 0;

        for (PlayerAbility ability : abilities) {
            double avgScore = ability.getAvgScore().doubleValue();
            // 弱点评分：低于全局平均越多，评分越高
            // 公式: weakness = (1 - avgScore / globalAvgScore) * 100
            // 如果avgScore >= globalAvgScore，则不是弱点
            double weakness;
            if (globalAvgScore > 0 && avgScore < globalAvgScore) {
                weakness = (1.0 - avgScore / globalAvgScore) * 100.0;
                weakness = Math.min(100.0, Math.max(0.0, weakness));
            } else {
                weakness = 0.0;
            }

            ability.setWeaknessScore(BigDecimal.valueOf(weakness).setScale(2, RoundingMode.HALF_UP));
            ability.setIsWeakness(weakness > WEAKNESS_THRESHOLD ? 1 : 0);
        }

        // 6. 删除旧的画像数据并写入新数据
        remove(new LambdaQueryWrapper<PlayerAbility>().eq(PlayerAbility::getPlayerId, playerId));
        saveBatch(abilities);

        log.info("玩家{}能力画像计算完成，共{}项能力指标", playerId, abilities.size());
    }

    @Override
    public List<PlayerAbility> getPlayerAbility(Long playerId) {
        return list(new LambdaQueryWrapper<PlayerAbility>()
                .eq(PlayerAbility::getPlayerId, playerId)
                .orderByDesc(PlayerAbility::getWeaknessScore));
    }

    @Override
    public List<PlayerAbility> getPlayerWeaknesses(Long playerId) {
        return list(new LambdaQueryWrapper<PlayerAbility>()
                .eq(PlayerAbility::getPlayerId, playerId)
                .eq(PlayerAbility::getIsWeakness, 1)
                .orderByDesc(PlayerAbility::getWeaknessScore));
    }

    @Override
    public List<PlayerAbility> getRadarData(Long playerId) {
        // 返回所有标签的能力数据（用于雷达图展示）
        // 如果玩家没有该标签的数据，则返回默认值0
        List<PlayerAbility> abilities = getPlayerAbility(playerId);
        Set<String> existingTags = abilities.stream()
                .map(PlayerAbility::getTagName)
                .collect(Collectors.toSet());

        // 补充缺失的标签
        for (TagEnum tag : TagEnum.values()) {
            if (!existingTags.contains(tag.getName())) {
                PlayerAbility defaultAbility = new PlayerAbility();
                defaultAbility.setPlayerId(playerId);
                defaultAbility.setTagName(tag.getName());
                defaultAbility.setAvgScore(BigDecimal.ZERO);
                defaultAbility.setAvgRating(BigDecimal.ZERO);
                defaultAbility.setTotalSongs(0);
                defaultAbility.setSsspCount(0);
                defaultAbility.setWeaknessScore(BigDecimal.ZERO);
                defaultAbility.setIsWeakness(0);
                abilities.add(defaultAbility);
            }
        }

        // 按标签枚举排序，确保雷达图顺序一致
        Map<String, Integer> tagOrder = new HashMap<>();
        for (TagEnum tag : TagEnum.values()) {
            tagOrder.put(tag.getName(), tag.ordinal());
        }

        abilities.sort((a, b) -> {
            int orderA = tagOrder.getOrDefault(a.getTagName(), 99);
            int orderB = tagOrder.getOrDefault(b.getTagName(), 99);
            return Integer.compare(orderA, orderB);
        });

        return abilities;
    }

    /**
     * 标签聚合数据（内部使用）
     */
    private static class TagAggregateData {
        double weightedScoreSum = 0;   // 加权总分
        double weightedRatingSum = 0;  // 加权Rating总和
        double weightCount = 0;        // 权重总和
        int totalSongs = 0;             // 总谱面数
        int ssspCount = 0;             // SSS+数
    }
}
