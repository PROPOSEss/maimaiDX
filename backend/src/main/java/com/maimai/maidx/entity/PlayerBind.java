package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 玩家MAID绑定实体
 */
@Data
@TableName("player_bind")
public class PlayerBind implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String maId;

    private String playerName;

    private Integer rating;

    private Integer maxRating;

    private String classRank;

    private LocalDateTime lastSyncTime;

    /** 同步状态: 0=未同步, 1=同步中, 2=已同步, -1=同步失败 */
    private Integer syncStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime bindTime;
}
