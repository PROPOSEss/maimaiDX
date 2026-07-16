package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maimai.maidx.entity.*;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.repository.SongFeatureRepository;
import com.maimai.maidx.repository.TrainingSuggestionRepository;
import com.maimai.maidx.service.PlayerAbilityService;
import com.maimai.maidx.service.SongFeatureService;
import com.maimai.maidx.service.TrainingSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 训练建议服务实现
 *
 * 算法流程：
 * 1. 获取玩家弱点标签
 * 2. 根据弱点标签查找对应谱面
 * 3. 筛选条件：玩家尚未达到AP/SSS+的谱面优先
 * 4. 按难度递进排序
 * 5. 生成推荐理由
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingSuggestionServiceImpl extends ServiceImpl<TrainingSuggestionRepository, TrainingSuggestion>
        implements TrainingSuggestionService {

    private final PlayerAbilityService playerAbilityService;
    private final SongFeatureRepository songFeatureRepository;
    private final SongDifficultyRepository songDifficultyRepository;
    private final SongFeatureService songFeatureService;

    /** 每个弱点最多推荐谱面数 */
    private static final int MAX_SUGGESTIONS_PER_TAG = 5;

    @Override
    @Transactional
    public List<TrainingSuggestion> generateSuggestions(Long playerId) {
        log.info("为玩家{}生成训练建议", playerId);

        // 1. 获取玩家弱点
        List<PlayerAbility> weaknesses = playerAbilityService.getPlayerWeaknesses(playerId);
        if (weaknesses.isEmpty()) {
            log.info("玩家{}无弱点，生成均衡训练建议", playerId);
            return generateBalancedSuggestions(playerId);
        }

        // 2. 删除旧的训练建议
        remove(new LambdaQueryWrapper<TrainingSuggestion>().eq(TrainingSuggestion::getPlayerId, playerId));

        List<TrainingSuggestion> suggestions = new ArrayList<>();

        // 3. 为每个弱点生成推荐谱面
        for (PlayerAbility weakness : weaknesses) {
            String tagName = weakness.getTagName();
            double weaknessScore = weakness.getWeaknessScore().doubleValue();

            // 查找该标签下的谱面
            List<SongFeature> features = songFeatureRepository.selectList(
                    new LambdaQueryWrapper<SongFeature>()
                            .eq(SongFeature::getTagName, tagName)
                            .eq(SongFeature::getSource, 1)
                            .ge(SongFeature::getWeight, new BigDecimal("10"))
                            .orderByDesc(SongFeature::getWeight));

            int priority = weaknessScore > 80 ? 1 : weaknessScore > 60 ? 2 : 3;
            int count = 0;

            for (SongFeature feature : features) {
                if (count >= MAX_SUGGESTIONS_PER_TAG) break;

                SongDifficulty diff = songDifficultyRepository.selectById(feature.getDifficultyId());
                if (diff == null || diff.getLevel() < 12) continue; // 只推荐12+谱面

                TrainingSuggestion suggestion = new TrainingSuggestion();
                suggestion.setPlayerId(playerId);
                suggestion.setTagName(tagName);
                suggestion.setDifficultyId(feature.getDifficultyId());
                suggestion.setPriority(priority);
                suggestion.setReason(generateReason(tagName, diff.getLevel(), feature.getWeight().doubleValue()));

                suggestions.add(suggestion);
                count++;
            }
        }

        if (!suggestions.isEmpty()) {
            saveBatch(suggestions);
        }

        log.info("为玩家{}生成{}条训练建议", playerId, suggestions.size());
        return suggestions;
    }

    @Override
    public List<TrainingSuggestion> getPlayerSuggestions(Long playerId) {
        return list(new LambdaQueryWrapper<TrainingSuggestion>()
                .eq(TrainingSuggestion::getPlayerId, playerId)
                .orderByAsc(TrainingSuggestion::getPriority)
                .orderByDesc(TrainingSuggestion::getCreatedAt));
    }

    @Override
    @Transactional
    public List<TrainingSuggestion> refreshSuggestions(Long playerId) {
        // 先重新计算能力画像
        playerAbilityService.calculateAbility(playerId);
        // 再生成训练建议
        return generateSuggestions(playerId);
    }

    /**
     * 生成均衡训练建议（无弱点时）
     */
    private List<TrainingSuggestion> generateBalancedSuggestions(Long playerId) {
        List<TrainingSuggestion> suggestions = new ArrayList<>();

        // 推荐各标签的高权重谱面
        for (String tagName : Arrays.asList("纵连", "交互", "体力")) {
            List<SongFeature> features = songFeatureRepository.selectList(
                    new LambdaQueryWrapper<SongFeature>()
                            .eq(SongFeature::getTagName, tagName)
                            .eq(SongFeature::getSource, 1)
                            .orderByDesc(SongFeature::getWeight)
                            .last("LIMIT 3"));

            for (SongFeature feature : features) {
                TrainingSuggestion suggestion = new TrainingSuggestion();
                suggestion.setPlayerId(playerId);
                suggestion.setTagName(tagName);
                suggestion.setDifficultyId(feature.getDifficultyId());
                suggestion.setPriority(2);
                suggestion.setReason("均衡提升训练");
                suggestions.add(suggestion);
            }
        }

        if (!suggestions.isEmpty()) {
            saveBatch(suggestions);
        }
        return suggestions;
    }

    /**
     * 生成推荐理由
     */
    private String generateReason(String tagName, int level, double weight) {
        String levelDesc = level >= 14 ? "极高难度" : level >= 13 ? "高难度" : "中高难度";
        String weightDesc = weight >= 40 ? "主要考察" : weight >= 20 ? "重点涉及" : "涉及";

        return String.format("%s谱面，%s「%s」能力", levelDesc, weightDesc, tagName);
    }
}
