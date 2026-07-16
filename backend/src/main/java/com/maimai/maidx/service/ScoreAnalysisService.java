package com.maimai.maidx.service;

import com.maimai.maidx.dto.ScoreResponse;
import com.maimai.maidx.dto.B50Response;
import com.maimai.maidx.dto.ScoreTrendResponse;

import java.util.List;

/**
 * 成绩分析服务 - B50计算、成绩趋势等高级分析
 */
public interface ScoreAnalysisService {

    /**
     * 获取玩家的 Best 50 成绩
     *
     * @param userId 用户ID
     * @return B50 列表（按 DX Rating 降序）
     */
    List<B50Response> getB50(Long userId);

    /**
     * 获取玩家的成绩趋势数据
     *
     * @param userId 用户ID
     * @param months 查询最近几个月
     * @return 趋势数据列表
     */
    List<ScoreTrendResponse> getScoreTrend(Long userId, int months);

    /**
     * 计算玩家预估 DX Rating
     *
     * @param userId 用户ID
     * @return 预估 DX Rating
     */
    double estimateDxRating(Long userId);

    /**
     * 获取玩家在指定难度下的最佳成绩列表
     *
     * @param userId 用户ID
     * @param difficulty 难度（BASIC/ADVANCED/EXPERT/MASTER/Re:MASTER）
     * @param page 页码
     * @param size 每页数量
     * @return 成绩列表
     */
    List<ScoreResponse> getBestScoresByDifficulty(Long userId, String difficulty, int page, int size);

    /**
     * 刷新并更新玩家成绩数据
     * 从外部API同步最新成绩到数据库
     *
     * @param userId 用户ID
     * @return 新增/更新的成绩数量
     */
    int refreshScores(Long userId);
}
