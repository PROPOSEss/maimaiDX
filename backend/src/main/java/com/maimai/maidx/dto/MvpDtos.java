package com.maimai.maidx.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MvpDtos {

    @Data
    public static class SongImportItem {
        private String songId;
        private String title;
        private String artist;
        private String genre;
        private Integer bpm;
        private String version;
        private Integer isNew;
        private List<ChartImportItem> charts = new ArrayList<>();
    }

    @Data
    public static class ChartImportItem {
        private Integer difficulty;
        private Integer level;
        private BigDecimal ds;
        private BigDecimal fitDiff;
        private Integer notes;
        private Integer tap;
        private Integer hold;
        private Integer slide;
        private Integer touch;
        private Integer breakCount;
        private String charter;
    }

    @Data
    public static class ScoreImportRequest {
        private String source = "manual_json";
        private Integer rating = 0;
        private List<ScoreImportItem> records = new ArrayList<>();
    }

    @Data
    public static class ScoreImportItem {
        private String songId;
        private Integer difficulty;
        private BigDecimal achievement;
        private Integer dxScore;
        private String rate;
        private String fc;
        private String fs;
    }

    @Data
    public static class ImportResult {
        private Long snapshotId;
        private Long userId;
        private Integer rating;
        private Integer importedCount;
        private Integer b50Count;
        private LocalDateTime importedAt;
    }

    @Data
    public static class SongQueryItem {
        private Long id;
        private String songId;
        private String title;
        private String artist;
        private String genre;
        private Integer bpm;
        private String version;
        private Integer isNew;
        private List<ChartItem> charts = new ArrayList<>();
    }

    @Data
    public static class ChartItem {
        private Long id;
        private Integer difficulty;
        private String difficultyName;
        private Integer level;
        private BigDecimal ds;
        private BigDecimal fitDiff;
        private Integer notes;
        private Integer tap;
        private Integer hold;
        private Integer slide;
        private Integer touch;
        private Integer breakCount;
        private String charter;
    }

    @Data
    public static class B50Response {
        private Long snapshotId;
        private Integer rating;
        private Integer count;
        private Integer edgeRa;
        private List<ScoreItem> records = new ArrayList<>();
        private Map<String, Long> levelDistribution;
        private Map<String, Long> dsDistribution;
    }

    @Data
    public static class ScoreItem {
        private Long id;
        private String songId;
        private String title;
        private String artist;
        private Integer difficulty;
        private String difficultyName;
        private Integer level;
        private BigDecimal ds;
        private BigDecimal achievement;
        private Integer dxScore;
        private String rate;
        private String fc;
        private String fs;
        private Integer ra;
        private Integer isB50;
        private String b50Type;
    }

    @Data
    public static class RecommendationResponse {
        private Long snapshotId;
        private Integer count;
        private List<RecommendationItemDto> items = new ArrayList<>();
    }

    @Data
    public static class RecommendationItemDto {
        private Long id;
        private String songId;
        private String title;
        private String artist;
        private Long chartId;
        private Integer difficulty;
        private String difficultyName;
        private Integer level;
        private BigDecimal ds;
        private BigDecimal fitDiff;
        private BigDecimal currentAchievement;
        private BigDecimal targetAchievement;
        private Integer expectedGain;
        private String difficultyLevel;
        private BigDecimal recommendScore;
        private String reason;
    }

    @Data
    public static class GrowthResponse {
        private Long userId;
        private Long fromSnapshotId;
        private Long toSnapshotId;
        private Integer fromRating;
        private Integer toRating;
        private Integer ratingDelta;
        private Integer fromEdgeRa;
        private Integer toEdgeRa;
        private Integer edgeRaDelta;
        private List<GrowthScoreChangeItem> newB50 = new ArrayList<>();
        private List<GrowthScoreChangeItem> droppedB50 = new ArrayList<>();
        private List<GrowthScoreChangeItem> improvedScores = new ArrayList<>();
    }

    @Data
    public static class GrowthScoreChangeItem {
        private String songId;
        private String title;
        private String artist;
        private Integer difficulty;
        private String difficultyName;
        private Integer level;
        private BigDecimal ds;
        private BigDecimal fromAchievement;
        private BigDecimal toAchievement;
        private Integer fromRa;
        private Integer toRa;
        private Integer raDelta;
    }

    @Data
    public static class ReportResponse {
        private Long userId;
        private Long snapshotId;
        private Integer rating;
        private B50Response b50;
        private RecommendationResponse recommendations;
    }
}
