package com.maimai.maidx.assistant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TrainingProfile {

    private Integer recentCount;

    private BigDecimal averageAchievement;

    private BigDecimal mainConstantMin;

    private BigDecimal mainConstantMax;

    private String recentTrend;

    private Integer b50EdgeRa;

    private List<TrainingScoreItem> recentScores = new ArrayList<>();

    private List<TrainingScoreItem> topScores = new ArrayList<>();

    private List<TrainingScoreItem> improvementCandidates = new ArrayList<>();

    @Data
    public static class TrainingScoreItem {
        private String songId;
        private String songName;
        private Integer difficulty;
        private BigDecimal constant;
        private BigDecimal achievement;
        private Integer ra;
        private LocalDateTime playedAt;
    }
}
