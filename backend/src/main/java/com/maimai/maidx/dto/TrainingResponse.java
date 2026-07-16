package com.maimai.maidx.dto;

import lombok.Data;

/**
 * 训练建议响应
 */
@Data
public class TrainingResponse {

    private Long id;
    private String tagName;
    private String tagNameDisplay;
    private Long difficultyId;
    private SongInfo song;
    private String difficultyDisplay;
    private Integer level;
    private Integer priority;
    private String reason;

    @Data
    public static class SongInfo {
        private String songId;
        private String title;
        private String artist;
        private String cover;
    }
}
