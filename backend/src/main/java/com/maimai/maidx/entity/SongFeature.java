package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 谱面标签实体
 */
@Data
@TableName("song_feature")
public class SongFeature implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long difficultyId;

    private String tagName;

    private BigDecimal weight;

    /** 来源: 1=系统预设, 2=社区投票 */
    private Integer source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
