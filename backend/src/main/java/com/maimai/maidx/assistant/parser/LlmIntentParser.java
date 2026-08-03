package com.maimai.maidx.assistant.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.config.AssistantProperties;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Primary
@Component
public class LlmIntentParser implements IntentParser {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String SYSTEM_PROMPT = """
            You parse maimaiDX assistant user messages into JSON only.
            Return exactly one JSON object. Do not return Markdown, explanation, SQL, or extra text.
            Supported JSON formats:
            {"intent":"RECENT_SCORES","limit":20}
            {"intent":"TOP_SCORES","limit":30}
            {"intent":"RANDOM_RECOMMENDATION","count":5,"minConstant":12.7,"maxConstant":13.4}
            {"intent":"UNKNOWN"}
            Allowed fields: intent, limit, count, minConstant, maxConstant.
            """;

    private final AssistantProperties properties;
    private final RuleBasedIntentParser fallbackParser;
    private final ObjectMapper objectMapper;

    public LlmIntentParser(AssistantProperties properties,
                           RuleBasedIntentParser fallbackParser,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.fallbackParser = fallbackParser;
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedIntent parse(String message) {
        if (!shouldCallLlm()) {
            return fallbackParser.parse(message);
        }
        try {
            ParsedIntent parsed = parseWithLlm(message);
            if (parsed == null || parsed.getIntent() == null) {
                return fallbackParser.parse(message);
            }
            return parsed;
        } catch (Exception e) {
            log.warn("LLM意图解析失败，已降级为规则解析: {}", e.getClass().getSimpleName());
            return fallbackParser.parse(message);
        }
    }

    private boolean shouldCallLlm() {
        AssistantProperties.Ai ai = properties.getAi();
        return ai != null
                && ai.isEnabled()
                && StringUtils.hasText(ai.getApiKey())
                && StringUtils.hasText(ai.getBaseUrl())
                && StringUtils.hasText(ai.getModel());
    }

    private ParsedIntent parseWithLlm(String message) throws Exception {
        AssistantProperties.Ai ai = properties.getAi();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(ai.getTimeoutMs()))
                .readTimeout(Duration.ofMillis(ai.getTimeoutMs()))
                .writeTimeout(Duration.ofMillis(ai.getTimeoutMs()))
                .build();

        Map<String, Object> payload = Map.of(
                "model", ai.getModel(),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", message)
                )
        );

        Request request = new Request.Builder()
                .url(ai.getBaseUrl())
                .addHeader("Authorization", "Bearer " + ai.getApiKey())
                .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("LLM response failed");
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (!contentNode.isTextual()) {
                throw new IllegalArgumentException("LLM content missing");
            }
            return parseJsonContent(contentNode.asText());
        }
    }

    private ParsedIntent parseJsonContent(String content) throws Exception {
        JsonNode node = objectMapper.readTree(content.trim());
        String intentValue = node.path("intent").asText(null);
        IntentType intent = IntentType.valueOf(intentValue);

        ParsedIntent parsed = new ParsedIntent();
        parsed.setIntent(intent);
        if (node.hasNonNull("limit")) {
            parsed.setLimit(node.get("limit").asInt());
        }
        if (node.hasNonNull("count")) {
            parsed.setCount(node.get("count").asInt());
        }
        if (node.hasNonNull("minConstant")) {
            parsed.setMinConstant(node.get("minConstant").decimalValue());
        }
        if (node.hasNonNull("maxConstant")) {
            parsed.setMaxConstant(node.get("maxConstant").decimalValue());
        }
        return parsed;
    }
}
