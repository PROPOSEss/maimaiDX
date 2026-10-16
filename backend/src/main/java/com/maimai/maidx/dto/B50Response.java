package com.maimai.maidx.dto;

import lombok.Data;

/**
 * B50 成绩条目
 */
@Data
public class B50Response {

    /** 歌曲ID */
    private String songId;

    /** 歌曲名 */
    private String songTitle;

    /** 难度 */
    private String difficulty;

    /** 难度定数 */
    private Double level;

    /** 单谱面 RA，采用 ScoreRules；Double 仅为旧响应数值类型。 */
    private Double dxRating;

    /** 成就率 */
    private java.math.BigDecimal achievement;

    /** DX 分数 */
    private Integer dxScore;

    /** 评价 */
    private String achievementRank;

    /** 最大 Combo */
    private Integer combo;

    /** 排名（在B50中的位置） */
    private Integer rank;
}
