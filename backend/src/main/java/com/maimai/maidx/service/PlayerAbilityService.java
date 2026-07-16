package com.maimai.maidx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maimai.maidx.entity.PlayerAbility;

import java.util.List;

public interface PlayerAbilityService extends IService<PlayerAbility> {

    /**
     * 计算并更新玩家能力画像
     * 核心算法：
     * 1. 根据玩家成绩关联谱面标签
     * 2. 统计各标签的平均分/平均Rating
     * 3. 计算弱点评分（与平均水平对比）
     * @param playerId 绑定ID
     */
    void calculateAbility(Long playerId);

    /**
     * 获取玩家能力画像
     * @param playerId 绑定ID
     * @return 能力画像列表
     */
    List<PlayerAbility> getPlayerAbility(Long playerId);

    /**
     * 获取玩家弱点列表（按弱点评分降序）
     * @param playerId 绑定ID
     * @return 弱点列表
     */
    List<PlayerAbility> getPlayerWeaknesses(Long playerId);

    /**
     * 获取雷达图数据
     * @param playerId 绑定ID
     * @return 雷达图数据（标签名 -> 评分）
     */
    List<PlayerAbility> getRadarData(Long playerId);
}
