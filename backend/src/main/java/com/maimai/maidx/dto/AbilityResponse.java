package com.maimai.maidx.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 能力画像响应（雷达图数据）
 */
@Data
public class AbilityResponse {

    private Long playerId;
    private String playerName;
    private Integer rating;

    /** 各维度能力数据 */
    private List<TagAbility> abilities;

    /** 弱点列表 */
    private List<TagAbility> weaknesses;

    @Data
    public static class TagAbility {
        private String tagName;
        private String tagCode;
        private String description;
        private BigDecimal avgScore;
        private BigDecimal avgRating;
        private Integer totalSongs;
        private Integer ssspCount;
        private BigDecimal weaknessScore;
        private Boolean isWeakness;
    }
}
