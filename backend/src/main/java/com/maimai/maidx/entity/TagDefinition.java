package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

/**
 * 标签定义实体（字典表）
 */
@Data
@TableName("tag_definition")
public class TagDefinition implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tagName;

    private String tagCode;

    private String description;

    private Integer sortOrder;

    private Integer isActive;
}
