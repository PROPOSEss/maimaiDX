package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Chart / difficulty entity.
 */
@Data
@TableName("song_difficulty")
public class SongDifficulty implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long songId;

    /** 0=BASIC, 1=ADVANCED, 2=EXPERT, 3=MASTER, 4=Re:MASTER */
    private Integer difficulty;

    private Integer level;

    /** Chart constant, also called ds in maimai communities. */
    private BigDecimal levelDecimal;

    /** Fitted difficulty used by the rule recommender. */
    private BigDecimal fitDiff;

    private Integer noteCount;

    private Integer tapCount;

    private Integer holdCount;

    private Integer slideCount;

    private Integer touchCount;

    private Integer breakCount;

    private String charter;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
