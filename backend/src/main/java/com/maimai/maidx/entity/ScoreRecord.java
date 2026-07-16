package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Score record under a snapshot. A player can have multiple historical records for the same chart.
 */
@Data
@TableName("score_record")
public class ScoreRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long snapshotId;

    private Long userId;

    private Long playerId;

    private Long songId;

    private Long difficultyId;

    private Integer score;

    private BigDecimal achievementRate;

    @TableField("`rank`")
    private String rank;

    private String fc;

    private String fs;

    private String syncStatus;

    private Integer dxScore;

    private Integer ra;

    private Integer isB50;

    private String b50Type;

    private Integer playCount;

    private LocalDateTime bestPlayTime;

    private LocalDateTime lastPlayTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
