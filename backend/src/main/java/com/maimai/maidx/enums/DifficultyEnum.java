package com.maimai.maidx.enums;

import lombok.Getter;

/**
 * 谱面难度枚举
 */
@Getter
public enum DifficultyEnum {

    BASIC(0, "BASIC"),
    ADVANCED(1, "ADVANCED"),
    EXPERT(2, "EXPERT"),
    MASTER(3, "MASTER"),
    REMASTER(4, "Re:MASTER");

    private final int code;
    private final String name;

    DifficultyEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static DifficultyEnum fromCode(int code) {
        for (DifficultyEnum d : values()) {
            if (d.code == code) return d;
        }
        throw new IllegalArgumentException("未知难度: " + code);
    }
}
