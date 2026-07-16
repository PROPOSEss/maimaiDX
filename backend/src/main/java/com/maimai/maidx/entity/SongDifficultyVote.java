package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 谱面标签投票实体
 */
@Data
@TableName("song_difficulty_vote")
public class SongDifficultyVote implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long difficultyId;

    private String tagName;

    /** 投票权重(Rating/10000) */
    private BigDecimal voteWeight;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
