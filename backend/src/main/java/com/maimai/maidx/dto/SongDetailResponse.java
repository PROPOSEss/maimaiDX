package com.maimai.maidx.dto;

import lombok.Data;

import java.util.List;

/**
 * 谱面详情响应（含标签和投票数据）
 */
@Data
public class SongDetailResponse {

    private Long id;
    private String songId;
    private String title;
    private String titleEn;
    private String artist;
    private String artistEn;
    private Integer bpm;
    private String version;
    private String genre;

    /** 各难度的谱面信息 */
    private List<DifficultyInfo> difficulties;

    /** 用户投票的标签（如果已投票） */
    private List<String> votedTags;

    /** 社区投票统计 */
    private List<VoteStat> voteStats;

    @Data
    public static class DifficultyInfo {
        private Long id;
        private Integer difficulty;
        private String difficultyName;
        private Integer level;
        private Double levelDecimal;
        private Integer noteCount;
        private Integer tapCount;
        private Integer holdCount;
        private Integer slideCount;
        private Integer touchCount;
        private Integer breakCount;

        /** 标签列表 */
        private List<TagInfo> features;

        /** 玩家成绩（可选） */
        private PlayerScore playerScore;
    }

    @Data
    public static class TagInfo {
        private String tagName;
        private Double weight;
        private Integer source; // 1=系统, 2=社区
    }

    @Data
    public static class PlayerScore {
        private Integer score;
        private String rank;
        private String fc;
        private String fs;
        private Integer playCount;
    }

    @Data
    public static class VoteStat {
        private String tagName;
        private Double totalWeight;
        private Integer voteCount;
    }
}
