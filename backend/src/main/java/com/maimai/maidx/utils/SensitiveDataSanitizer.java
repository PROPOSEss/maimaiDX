package com.maimai.maidx.utils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes secrets from error messages before they are persisted or logged.
 */
public final class SensitiveDataSanitizer {

    private static final int DEFAULT_MAX_LENGTH = 1024;

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(password\\s*[=:]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)(pwd\\s*[=:]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)(authorization\\s*[=:]\\s*)(?:bearer\\s+)?[^\\s,;]+"),
            Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+"),
            Pattern.compile("(?i)(token\\s*[=:]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)(secret\\s*[=:]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)(api[_-]?key\\s*[=:]\\s*)[^\\s,;]+"),
            Pattern.compile("(jdbc:mysql://)[^\\s,;]+")
    );

    private SensitiveDataSanitizer() {
    }

    public static String sanitize(String message) {
        return sanitize(message, DEFAULT_MAX_LENGTH);
    }

    public static String sanitize(String message, int maxLength) {
        if (message == null || message.isBlank()) {
            return "任务处理失败";
        }
        String cleaned = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        for (Pattern pattern : SECRET_PATTERNS) {
            cleaned = pattern.matcher(cleaned).replaceAll("$1******");
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }
}
