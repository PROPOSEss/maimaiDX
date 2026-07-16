package com.maimai.maidx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maimai.maidx.entity.TrainingSuggestion;

import java.util.List;

public interface TrainingSuggestionService extends IService<TrainingSuggestion> {

    /**
     * 根据玩家弱点生成训练建议
     * 算法：
     * 1. 获取玩家能力画像中的弱点标签
     * 2. 根据弱点标签查找对应谱面（按难度递进）
     * 3. 优先推荐玩家成绩较低的谱面
     * @param playerId 绑定ID
     * @return 训练建议列表
     */
    List<TrainingSuggestion> generateSuggestions(Long playerId);

    /**
     * 获取玩家已有的训练建议
     */
    List<TrainingSuggestion> getPlayerSuggestions(Long playerId);

    /**
     * 刷新训练建议（重新生成）
     */
    List<TrainingSuggestion> refreshSuggestions(Long playerId);
}
