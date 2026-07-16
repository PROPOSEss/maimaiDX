package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 训练建议实体
 */
@Data
@TableName("training_suggestion")
public class TrainingSuggestion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playerId;

    private String tagName;

    private Long difficultyId;

    /** 优先级: 1=高, 2=中, 3=低 */
    private Integer priority;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
