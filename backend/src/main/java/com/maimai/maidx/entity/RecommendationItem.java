package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Rule-based recommendation result persisted for demos and review.
 */
@Data
@TableName("recommendation_item")
public class RecommendationItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long snapshotId;

    private Long songId;

    private Long chartId;

    private BigDecimal currentAchievement;

    private BigDecimal targetAchievement;

    private Integer expectedGain;

    private String difficultyLevel;

    private BigDecimal recommendScore;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
