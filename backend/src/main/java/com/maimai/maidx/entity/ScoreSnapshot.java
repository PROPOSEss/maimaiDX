package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One manual score import event. Keeping snapshots makes progress traceable.
 */
@Data
@TableName("score_snapshot")
public class ScoreSnapshot implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String source;

    private Integer rating;

    private Integer recordCount;

    private LocalDateTime importedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
