package com.maimai.maidx.assistant.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.config.AssistantProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLlmClientTest {

    @Test
    void returnsMessageContentFromCompatibleResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestContentType = new AtomicReference<>();
        server.createContext("/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"intent\\\":\\\"RECENT_SCORES\\\"}\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties(server), new ObjectMapper());

            String content = client.chat("system", "user");

            assertThat(content).isEqualTo("{\"intent\":\"RECENT_SCORES\"}");
            assertThat(requestBody.get()).contains("\"max_tokens\":600");
            assertThat(requestContentType.get()).isEqualTo("application/json; charset=utf-8");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesUtf8ChineseWhenResponseHasNoCharset() throws IOException {
        assertUtf8ChineseResponse("application/json");
    }

    @Test
    void preservesUtf8ChineseWhenResponseDeclaresUtf8() throws IOException {
        assertUtf8ChineseResponse("application/json; charset=utf-8");
    }

    @Test
    void preservesUtf8BytesWhenResponseDeclaresInaccurateCharset() throws IOException {
        assertUtf8ChineseResponse("application/json; charset=iso-8859-1");
    }

    @Test
    void sendsChineseRequestAsUtf8Json() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<byte[]> requestBytes = new AtomicReference<>();
        server.createContext("/chat", exchange -> {
            requestBytes.set(exchange.getRequestBody().readAllBytes());
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties(server), new ObjectMapper());

            client.chat("系统提示", "根据最近成绩生成训练建议");

            assertThat(new String(requestBytes.get(), StandardCharsets.UTF_8))
                    .contains("系统提示")
                    .contains("根据最近成绩生成训练建议");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsOnNon2xxResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties(server), new ObjectMapper());

            assertThatThrownBy(() -> client.chat("system", "user"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("LLM request failed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailableWhenApiKeyMissing() {
        AssistantProperties properties = new AssistantProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setApiKey("");

        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, new ObjectMapper());

        assertThat(client.isAvailable()).isFalse();
    }

    private AssistantProperties properties(HttpServer server) {
        AssistantProperties properties = new AssistantProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setApiKey("test-key");
        properties.getAi().setBaseUrl("http://localhost:" + server.getAddress().getPort() + "/chat");
        properties.getAi().setModel("test-model");
        properties.getAi().setTimeoutMs(1000);
        return properties;
    }

    private void assertUtf8ChineseResponse(String contentType) throws IOException {
        String expectedContent = """
                {"summary":"根据最近成绩生成训练建议","suggestions":[{"title":"稳定练习","reason":"近期达成率有波动","action":"每天练习三次"}]}
                """.trim();
        String responseJson = new ObjectMapper().writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", expectedContent)))));
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties(server), new ObjectMapper());

            String content = client.chat("system", "user");

            assertThat(content)
                    .isEqualTo(expectedContent)
                    .contains("根据最近成绩生成训练建议")
                    .doesNotContain("è¿", "å¼º");
        } finally {
            server.stop(0);
        }
    }
}
