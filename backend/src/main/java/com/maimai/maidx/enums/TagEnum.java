package com.maimai.maidx.enums;

import lombok.Getter;

/**
 * 谱面标签枚举（V1.1 标签体系）
 */
@Getter
public enum TagEnum {

    CROSS_HAND("反手", "cross_hand", "需要频繁左右手交叉处理"),
    ALTERNATE("交互", "alternate", "左右手高速交替"),
    CLASH("撞手", "clash", "双手轨迹冲突"),
    VERTICAL("纵连", "vertical", "同一区域连续高速敲击"),
    STAMINA("体力", "stamina", "高密度、持续输出"),
    READING("读谱", "reading", "谱面结构复杂，需要较强视谱能力"),
    RHYTHM("节奏难", "rhythm", "节奏复杂、多变"),
    STAR_SLIDE("错位星星", "star_slide", "复杂星星轨迹及滑键连续操作"),
    TOUCH("Touch圈", "touch", "Touch区域参与度较高");

    private final String name;
    private final String code;
    private final String description;

    TagEnum(String name, String code, String description) {
        this.name = name;
        this.code = code;
        this.description = description;
    }

    public static TagEnum fromCode(String code) {
        for (TagEnum t : values()) {
            if (t.code.equals(code)) return t;
        }
        throw new IllegalArgumentException("未知标签: " + code);
    }

    public static TagEnum fromName(String name) {
        for (TagEnum t : values()) {
            if (t.name.equals(name)) return t;
        }
        throw new IllegalArgumentException("未知标签: " + name);
    }
}
