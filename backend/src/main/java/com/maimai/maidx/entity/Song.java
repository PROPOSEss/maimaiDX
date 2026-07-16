package com.maimai.maidx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Song metadata.
 */
@Data
@TableName("song")
public class Song implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String songId;

    private String title;

    private String titleEn;

    private String artist;

    private String artistEn;

    private Integer bpm;

    private String version;

    private String genre;

    private Integer isNew;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
