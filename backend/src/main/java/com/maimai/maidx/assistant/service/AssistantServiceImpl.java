package com.maimai.maidx.assistant.service;

import com.maimai.maidx.assistant.config.AssistantProperties;
import com.maimai.maidx.assistant.dto.AssistantQueryRequest;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.parser.IntentParser;
import com.maimai.maidx.assistant.tool.AssistantToolRouter;
import com.maimai.maidx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    private static final int DEFAULT_RECENT_LIMIT = 20;
    private static final int DEFAULT_TOP_LIMIT = 30;
    private static final int MAX_SCORE_LIMIT = 50;
    private static final int DEFAULT_RANDOM_COUNT = 5;
    private static final int MAX_RANDOM_COUNT = 10;
    private static final int DEFAULT_ADVICE_COUNT = 3;
    private static final int MAX_ADVICE_COUNT = 5;

    private final AssistantProperties assistantProperties;
    private final UserRepository userRepository;
    private final IntentParser intentParser;
    private final AssistantToolRouter toolRouter;

    @Override
    public AssistantQueryResponse query(Long headerUserId, AssistantQueryRequest request) {
        Long userId = resolveUserId(headerUserId);
        ensureUserExists(userId);

        ParsedIntent parsedIntent = normalize(intentParser.parse(request.getMessage()));
        log.info("AI成绩助手解析意图: userId={}, intent={}", userId, parsedIntent.getIntent());

        if (parsedIntent.getIntent() == IntentType.UNKNOWN) {
            AssistantQueryResponse response = new AssistantQueryResponse();
            response.setUserId(userId);
            response.setIntent(IntentType.UNKNOWN);
            response.setParsedIntent(parsedIntent);
            response.setParserSource(parsedIntent.getParserSource());
            response.setAnswer("暂时只支持查询最近成绩、最高成绩、随机推荐定数范围曲目和训练建议。");
            return response;
        }
        return toolRouter.route(userId, parsedIntent);
    }

    private Long resolveUserId(Long headerUserId) {
        Long userId = headerUserId != null ? headerUserId : assistantProperties.getDefaultUserId();
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须大于 0");
        }
        return userId;
    }

    private void ensureUserExists(Long userId) {
        if (userRepository.selectById(userId) == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
    }

    private ParsedIntent normalize(ParsedIntent parsedIntent) {
        if (parsedIntent == null || parsedIntent.getIntent() == null) {
            return ParsedIntent.unknown();
        }
        if (parsedIntent.getIntent() == IntentType.RECENT_SCORES) {
            parsedIntent.setLimit(normalizePositiveLimit(parsedIntent.getLimit(), DEFAULT_RECENT_LIMIT, MAX_SCORE_LIMIT, "limit"));
        } else if (parsedIntent.getIntent() == IntentType.TOP_SCORES) {
            parsedIntent.setLimit(normalizePositiveLimit(parsedIntent.getLimit(), DEFAULT_TOP_LIMIT, MAX_SCORE_LIMIT, "limit"));
        } else if (parsedIntent.getIntent() == IntentType.RANDOM_RECOMMENDATION) {
            parsedIntent.setCount(normalizePositiveLimit(parsedIntent.getCount(), DEFAULT_RANDOM_COUNT, MAX_RANDOM_COUNT, "count"));
            if (parsedIntent.getMinConstant() != null
                    && parsedIntent.getMaxConstant() != null
                    && parsedIntent.getMinConstant().compareTo(parsedIntent.getMaxConstant()) > 0) {
                throw new IllegalArgumentException("minConstant 不能大于 maxConstant");
            }
        } else if (parsedIntent.getIntent() == IntentType.TRAINING_ADVICE) {
            parsedIntent.setAdviceCount(normalizePositiveLimit(
                    parsedIntent.getAdviceCount(), DEFAULT_ADVICE_COUNT, MAX_ADVICE_COUNT, "adviceCount"));
        }
        return parsedIntent;
    }

    private int normalizePositiveLimit(Integer value, int defaultValue, int maxValue, String fieldName) {
        if (value == null) {
            return defaultValue;
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
        return Math.min(value, maxValue);
    }
}
