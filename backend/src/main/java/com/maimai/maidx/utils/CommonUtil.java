package com.maimai.maidx.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 通用工具类
 */
@Component
public class CommonUtil {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 生成 UUID（去除连字符）
     */
    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 格式化日期时间
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * 验证 MAID 格式（A00000000000000 格式，17位字母数字）
     */
    public static boolean isValidMaid(String maid) {
        if (maid == null || maid.length() != 17) {
            return false;
        }
        return maid.matches("^[A-Z]\\d{16}$");
    }

    /**
     * 安全的字符串比较（防止时序攻击）
     */
    public static boolean safeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * 计算能力评分等级标签
     */
    public static String getAbilityLevel(double score) {
        if (score >= 90) return "S";
        if (score >= 80) return "A";
        if (score >= 70) return "B";
        if (score >= 60) return "C";
        return "D";
    }

    /**
     * 计算评价等级
     */
    public static String getAchievementRank(double achievement) {
        if (achievement >= 100.5) return "SSS+";
        if (achievement >= 100.0) return "SSS";
        if (achievement >= 99.5) return "SS";
        if (achievement >= 99.0) return "S";
        if (achievement >= 98.0) return "S+";
        if (achievement >= 97.0) return "AAA";
        if (achievement >= 95.0) return "AA";
        if (achievement >= 93.0) return "A";
        if (achievement >= 90.0) return "B";
        return "C";
    }
}
