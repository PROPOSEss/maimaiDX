package com.maimai.maidx.assistant.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.enums.ParserSource;
import com.maimai.maidx.assistant.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Primary
@Component
public class LlmIntentParser implements IntentParser {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "intent", "limit", "count", "adviceCount", "minConstant", "maxConstant");
    private static final String SYSTEM_PROMPT = """
            You parse maimaiDX assistant user messages into JSON only.
            Return exactly one JSON object. Do not return Markdown, explanation, SQL, or extra text.
            Supported JSON formats:
            {"intent":"RECENT_SCORES","limit":20}
            {"intent":"TOP_SCORES","limit":30}
            {"intent":"RANDOM_RECOMMENDATION","count":5,"minConstant":12.7,"maxConstant":13.4}
            {"intent":"TRAINING_ADVICE","adviceCount":3}
            {"intent":"UNKNOWN"}
            Allowed fields: intent, limit, count, adviceCount, minConstant, maxConstant.
            """;

    private final LlmClient llmClient;
    private final RuleBasedIntentParser fallbackParser;
    private final ObjectMapper objectMapper;

    public LlmIntentParser(LlmClient llmClient,
                           RuleBasedIntentParser fallbackParser,
                           ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.fallbackParser = fallbackParser;
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedIntent parse(String message) {
        if (!llmClient.isAvailable()) {
            return fallbackParser.parse(message);
        }
        try {
            ParsedIntent parsed = parseJsonContent(llmClient.chat(SYSTEM_PROMPT, message));
            if (parsed == null || parsed.getIntent() == null) {
                return fallbackParser.parse(message);
            }
            parsed.setParserSource(ParserSource.LLM);
            return parsed;
        } catch (Exception e) {
            log.warn("LLM意图解析失败，已降级为规则解析: {}", e.getClass().getSimpleName());
            return fallbackParser.parse(message);
        }
    }

    private ParsedIntent parseJsonContent(String content) throws Exception {
        JsonNode node = objectMapper.readTree(content.trim());
        if (!node.isObject()) {
            throw new IllegalArgumentException("LLM intent content must be object");
        }
        node.fieldNames().forEachRemaining(field -> {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unsupported LLM intent field");
            }
        });
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
        if (node.hasNonNull("adviceCount")) {
            parsed.setAdviceCount(node.get("adviceCount").asInt());
        }
        if (node.hasNonNull("minConstant")) {
            parsed.setMinConstant(node.get("minConstant").decimalValue());
        }
        if (node.hasNonNull("maxConstant")) {
            parsed.setMaxConstant(node.get("maxConstant").decimalValue());
        }
        if (parsed.getMinConstant() != null
                && parsed.getMaxConstant() != null
                && parsed.getMinConstant().compareTo(parsed.getMaxConstant()) > 0) {
            throw new IllegalArgumentException("minConstant greater than maxConstant");
        }
        return parsed;
    }
}
