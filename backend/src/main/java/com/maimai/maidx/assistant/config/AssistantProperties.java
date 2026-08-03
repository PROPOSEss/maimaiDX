package com.maimai.maidx.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {

    private Long defaultUserId = 999L;

    private Ai ai = new Ai();

    @Data
    public static class Ai {
        private boolean enabled = false;
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1/chat/completions";
        private String model = "gpt-4o-mini";
        private int timeoutMs = 3000;
    }
}
