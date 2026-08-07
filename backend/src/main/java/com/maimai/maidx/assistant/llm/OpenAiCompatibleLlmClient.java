package com.maimai.maidx.assistant.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.config.AssistantProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AssistantProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient client;

    public OpenAiCompatibleLlmClient(AssistantProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        AssistantProperties.Ai ai = properties.getAi();
        int timeoutMs = ai == null ? 3000 : ai.getTimeoutMs();
        this.client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofMillis(timeoutMs))
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .writeTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public boolean isAvailable() {
        AssistantProperties.Ai ai = properties.getAi();
        return ai != null
                && ai.isEnabled()
                && StringUtils.hasText(ai.getApiKey())
                && StringUtils.hasText(ai.getBaseUrl())
                && StringUtils.hasText(ai.getModel());
    }

    @Override
    public String chat(String systemPrompt, String userContent) {
        if (!isAvailable()) {
            throw new IllegalStateException("LLM is not configured");
        }
        AssistantProperties.Ai ai = properties.getAi();
        try {
            Map<String, Object> payload = Map.of(
                    "model", ai.getModel(),
                    "temperature", 0,
                    "max_tokens", ai.getMaxTokens(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userContent)
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
                byte[] responseBytes = response.body().bytes();
                String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(responseJson);
                JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
                if (!contentNode.isTextual() || !StringUtils.hasText(contentNode.asText())) {
                    throw new IllegalArgumentException("LLM content missing");
                }
                return contentNode.asText();
            }
        } catch (Exception e) {
            throw new IllegalStateException("LLM request failed", e);
        }
    }
}
