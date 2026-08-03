package com.maimai.maidx.assistant.parser;

import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedIntentParser implements IntentParser {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("(\\d{1,3})");
    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d{1,2})\\s*(首|个|张)");
    private static final Pattern CONSTANT_RANGE_PATTERN =
            Pattern.compile("(\\d{1,2}(?:\\.\\d+)?)\\s*(?:到|至|-|~)\\s*(\\d{1,2}(?:\\.\\d+)?)");

    @Override
    public ParsedIntent parse(String message) {
        if (!StringUtils.hasText(message)) {
            return ParsedIntent.unknown();
        }
        String normalized = message.trim().toLowerCase();
        if (isRandomRecommendation(normalized)) {
            return parseRandomRecommendation(normalized);
        }
        if (isRecentScores(normalized)) {
            ParsedIntent parsed = new ParsedIntent();
            parsed.setIntent(IntentType.RECENT_SCORES);
            parsed.setLimit(extractFirstInteger(normalized));
            return parsed;
        }
        if (isTopScores(normalized)) {
            ParsedIntent parsed = new ParsedIntent();
            parsed.setIntent(IntentType.TOP_SCORES);
            parsed.setLimit(extractFirstInteger(normalized));
            return parsed;
        }
        return ParsedIntent.unknown();
    }

    private boolean isRecentScores(String message) {
        return message.contains("最近") || message.contains("recent");
    }

    private boolean isTopScores(String message) {
        return message.contains("最高")
                || message.contains("最好")
                || message.contains("高分")
                || message.contains("top")
                || message.contains("best");
    }

    private boolean isRandomRecommendation(String message) {
        return (message.contains("随机") || message.contains("推荐") || message.contains("random"))
                && (message.contains("定数") || message.contains("constant") || message.contains("ds"));
    }

    private ParsedIntent parseRandomRecommendation(String message) {
        ParsedIntent parsed = new ParsedIntent();
        parsed.setIntent(IntentType.RANDOM_RECOMMENDATION);
        Matcher countMatcher = COUNT_PATTERN.matcher(message);
        if (countMatcher.find()) {
            parsed.setCount(Integer.parseInt(countMatcher.group(1)));
        }
        Matcher rangeMatcher = CONSTANT_RANGE_PATTERN.matcher(message);
        if (rangeMatcher.find()) {
            parsed.setMinConstant(new BigDecimal(rangeMatcher.group(1)));
            parsed.setMaxConstant(new BigDecimal(rangeMatcher.group(2)));
        }
        return parsed;
    }

    private Integer extractFirstInteger(String message) {
        Matcher matcher = INTEGER_PATTERN.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }
}
