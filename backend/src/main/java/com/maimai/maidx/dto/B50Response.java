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

    /** DX Rating 基础值（min(achievement/100, 100) × level） */
    private Double dxRating;

    /** 成就率 */
    private Double achievement;

    /** DX 分数 */
    private Integer dxScore;

    /** 评价 */
    private String achievementRank;

    /** 最大 Combo */
    private Integer combo;

    /** 排名（在B50中的位置） */
    private Integer rank;
}
