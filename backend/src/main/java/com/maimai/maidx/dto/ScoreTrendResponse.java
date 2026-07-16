package com.maimai.maidx.dto;

import lombok.Data;

import java.util.List;

/**
 * 成绩趋势数据
 */
@Data
public class ScoreTrendResponse {

    /** 月份（yyyy-MM） */
    private String month;

    /** 当月游玩次数 */
    private Integer playCount;

    /** 当月平均成就率 */
    private Double avgAchievement;

    /** 当月最佳成就率 */
    private Double bestAchievement;

    /** 当月新AP数量 */
    private Integer newApCount;

    /** 当月新SSS+数量 */
    private Integer newSssPlusCount;

    /** 当月预估Rating变化 */
    private Double ratingChange;

    /** 各标签维度的平均得分 */
    private List<TagTrend> tagTrends;

    @Data
    public static class TagTrend {
        /** 标签名 */
        private String tag;

        /** 平均得分 */
        private Double avgScore;
    }
}
