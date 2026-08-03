package com.maimai.maidx.assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.maimai.maidx.assistant.enums.IntentType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantQueryResponse {

    private Long userId;

    private IntentType intent;

    private String answer;

    private ParsedIntent parsedIntent;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ScoreItem> scores = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<RecommendationItem> recommendations = new ArrayList<>();

    @Data
    public static class ScoreItem {
        private Long recordId;
        private String songId;
        private Long chartId;
        private String songName;
        private String artist;
        private Integer difficulty;
        private String difficultyName;
        private BigDecimal constant;
        private BigDecimal achievement;
        private Integer dxScore;
        private String rate;
        private String fc;
        private String fs;
        private Integer ra;
        private LocalDateTime playedAt;
    }

    @Data
    public static class RecommendationItem {
        private String songId;
        private Long chartId;
        private String songName;
        private String artist;
        private Integer difficulty;
        private String difficultyName;
        private BigDecimal constant;
        private String version;
    }
}
