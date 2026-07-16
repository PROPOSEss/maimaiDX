package com.maimai.maidx.dto;

import lombok.Data;

/**
 * 成绩列表响应
 */
@Data
public class ScoreResponse {

    private Long id;
    private Long difficultyId;
    private String songId;
    private String title;
    private String artist;
    private Integer difficulty;
    private String difficultyName;
    private Integer level;
    private Integer score;
    private String rank;
    private String fc;
    private String fs;
    private Integer playCount;
    private String bestPlayTime;
}
