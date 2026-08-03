package com.maimai.maidx.assistant.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.config.AssistantProperties;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LlmIntentParserTest {

    @Test
    void usesRuleParserWhenAiDisabled() {
        AssistantProperties properties = new AssistantProperties();
        properties.getAi().setEnabled(false);
        LlmIntentParser parser = new LlmIntentParser(properties, new RuleBasedIntentParser(), new ObjectMapper());

        ParsedIntent intent = parser.parse("最近20条成绩");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RECENT_SCORES);
        assertThat(intent.getLimit()).isEqualTo(20);
    }

    @Test
    void usesRuleParserWhenApiKeyMissing() {
        AssistantProperties properties = new AssistantProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setApiKey("");
        LlmIntentParser parser = new LlmIntentParser(properties, new RuleBasedIntentParser(), new ObjectMapper());

        ParsedIntent intent = parser.parse("最高30张成绩");

        assertThat(intent.getIntent()).isEqualTo(IntentType.TOP_SCORES);
        assertThat(intent.getLimit()).isEqualTo(30);
    }

    @Test
    void fallsBackWhenLlmReturnsInvalidJson() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"not json\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AssistantProperties properties = new AssistantProperties();
            properties.getAi().setEnabled(true);
            properties.getAi().setApiKey("test-key");
            properties.getAi().setBaseUrl("http://localhost:" + server.getAddress().getPort() + "/chat");
            properties.getAi().setTimeoutMs(1000);
            LlmIntentParser parser = new LlmIntentParser(properties, new RuleBasedIntentParser(), new ObjectMapper());

            ParsedIntent intent = parser.parse("随机推荐定数12.7到13.4的5首歌");

            assertThat(intent.getIntent()).isEqualTo(IntentType.RANDOM_RECOMMENDATION);
            assertThat(intent.getCount()).isEqualTo(5);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackWhenLlmRequestFails() {
        AssistantProperties properties = new AssistantProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setApiKey("test-key");
        properties.getAi().setBaseUrl("http://localhost:1/chat");
        properties.getAi().setTimeoutMs(200);
        LlmIntentParser parser = new LlmIntentParser(properties, new RuleBasedIntentParser(), new ObjectMapper());

        ParsedIntent intent = parser.parse("最近20条成绩");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RECENT_SCORES);
        assertThat(intent.getLimit()).isEqualTo(20);
    }
}
