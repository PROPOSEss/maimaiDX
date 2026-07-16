package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maimai.maidx.entity.SongFeature;
import com.maimai.maidx.entity.SongDifficultyVote;
import com.maimai.maidx.repository.SongFeatureRepository;
import com.maimai.maidx.repository.SongDifficultyVoteRepository;
import com.maimai.maidx.service.SongFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 谱面标签服务实现
 */
@Service
@RequiredArgsConstructor
public class SongFeatureServiceImpl extends ServiceImpl<SongFeatureRepository, SongFeature> implements SongFeatureService {

    private final SongDifficultyVoteRepository voteRepository;

    @Override
    public List<SongFeature> getFeaturesByDifficultyId(Long difficultyId) {
        // 获取系统预设标签
        List<SongFeature> systemFeatures = list(new LambdaQueryWrapper<SongFeature>()
                .eq(SongFeature::getDifficultyId, difficultyId)
                .eq(SongFeature::getSource, 1)
                .orderByDesc(SongFeature::getWeight));

        // 获取社区投票标签
        Map<String, Double> votedWeights = getVotedFeatureWeights(difficultyId);

        // 合并：如果系统标签无数据，则用投票数据补充
        if (!votedWeights.isEmpty()) {
            Set<String> existingTags = systemFeatures.stream()
                    .map(SongFeature::getTagName)
                    .collect(Collectors.toSet());

            for (Map.Entry<String, Double> entry : votedWeights.entrySet()) {
                if (!existingTags.contains(entry.getKey())) {
                    SongFeature voteFeature = new SongFeature();
                    voteFeature.setDifficultyId(difficultyId);
                    voteFeature.setTagName(entry.getKey());
                    voteFeature.setWeight(BigDecimal.valueOf(entry.getValue()).setScale(2, RoundingMode.HALF_UP));
                    voteFeature.setSource(2);
                    systemFeatures.add(voteFeature);
                }
            }
        }

        return systemFeatures;
    }

    @Override
    public Map<Long, List<SongFeature>> getFeaturesByDifficultyIds(List<Long> difficultyIds) {
        if (difficultyIds == null || difficultyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SongFeature> allFeatures = list(new LambdaQueryWrapper<SongFeature>()
                .in(SongFeature::getDifficultyId, difficultyIds)
                .eq(SongFeature::getSource, 1)
                .orderByDesc(SongFeature::getWeight));

        return allFeatures.stream().collect(Collectors.groupingBy(SongFeature::getDifficultyId));
    }

    @Override
    public Map<String, Double> getVotedFeatureWeights(Long difficultyId) {
        List<SongDifficultyVote> votes = voteRepository.selectList(
                new LambdaQueryWrapper<SongDifficultyVote>()
                        .eq(SongDifficultyVote::getDifficultyId, difficultyId));

        if (votes.isEmpty()) {
            return Collections.emptyMap();
        }

        // 按标签聚合权重
        Map<String, Double> tagWeightSum = new HashMap<>();
        Map<String, Integer> tagVoteCount = new HashMap<>();

        for (SongDifficultyVote vote : votes) {
            String tag = vote.getTagName();
            double weight = vote.getVoteWeight().doubleValue();
            tagWeightSum.merge(tag, weight, Double::sum);
            tagVoteCount.merge(tag, 1, Integer::sum);
        }

        // 归一化为百分比
        double totalWeight = tagWeightSum.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Double> entry : tagWeightSum.entrySet()) {
            double normalizedWeight = totalWeight > 0 ? (entry.getValue() / totalWeight) * 100.0 : 0;
            result.put(entry.getKey(), Math.round(normalizedWeight * 100.0) / 100.0);
        }

        return result;
    }
}
