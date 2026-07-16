package com.maimai.maidx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maimai.maidx.entity.SongFeature;

import java.util.List;
import java.util.Map;

public interface SongFeatureService extends IService<SongFeature> {

    /**
     * 获取某个谱面的所有标签（合并系统预设和社区投票）
     * @param difficultyId 谱面ID
     * @return 标签列表（带权重）
     */
    List<SongFeature> getFeaturesByDifficultyId(Long difficultyId);

    /**
     * 获取多个谱面的标签映射
     * @param difficultyIds 谱面ID列表
     * @return Map<谱面ID, 标签列表>
     */
    Map<Long, List<SongFeature>> getFeaturesByDifficultyIds(List<Long> difficultyIds);

    /**
     * 获取社区投票统计后的标签权重
     * @param difficultyId 谱面ID
     * @return 标签名 -> 综合权重
     */
    Map<String, Double> getVotedFeatureWeights(Long difficultyId);
}
