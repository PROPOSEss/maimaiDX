package com.maimai.maidx.assistant.llm;

public interface LlmClient {

    boolean isAvailable();

    String chat(String systemPrompt, String userContent);
}
