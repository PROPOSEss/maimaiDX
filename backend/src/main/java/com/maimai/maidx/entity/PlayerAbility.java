package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 玩家能力画像实体
 */
@Data
@TableName("player_ability")
public class PlayerAbility implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playerId;

    private String tagName;

    /** 该标签下平均分 */
    private BigDecimal avgScore;

    /** 该标签下平均Rating */
    private BigDecimal avgRating;

    /** 该标签下总谱面数 */
    private Integer totalSongs;

    /** SSS+数量 */
    private Integer ssspCount;

    /** 弱点评分(0-100, 越高越弱) */
    private BigDecimal weaknessScore;

    /** 是否为弱点 */
    private Integer isWeakness;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdate;
}
