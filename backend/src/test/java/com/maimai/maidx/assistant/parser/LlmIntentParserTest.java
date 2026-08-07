package com.maimai.maidx.assistant.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.enums.ParserSource;
import com.maimai.maidx.assistant.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmIntentParserTest {

    @Test
    void usesRuleParserWhenAiDisabled() {
        LlmIntentParser parser = parser(FakeLlmClient.unavailable());

        ParsedIntent intent = parser.parse("最近20条成绩");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RECENT_SCORES);
        assertThat(intent.getLimit()).isEqualTo(20);
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.RULE);
    }

    @Test
    void parsesLegalLlmIntentJson() {
        LlmIntentParser parser = parser(FakeLlmClient.available("""
                {"intent":"RANDOM_RECOMMENDATION","count":5,"minConstant":12.7,"maxConstant":13.4}
                """));

        ParsedIntent intent = parser.parse("推荐定数12.7到13.4的5首歌");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RANDOM_RECOMMENDATION);
        assertThat(intent.getCount()).isEqualTo(5);
        assertThat(intent.getMinConstant()).isEqualByComparingTo("12.7");
        assertThat(intent.getMaxConstant()).isEqualByComparingTo("13.4");
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.LLM);
    }

    @Test
    void parsesTrainingAdviceIntentFromLlm() {
        LlmIntentParser parser = parser(FakeLlmClient.available(
                "{\"intent\":\"TRAINING_ADVICE\",\"adviceCount\":3}"));

        ParsedIntent intent = parser.parse("根据我最近的成绩给一些训练建议");

        assertThat(intent.getIntent()).isEqualTo(IntentType.TRAINING_ADVICE);
        assertThat(intent.getAdviceCount()).isEqualTo(3);
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.LLM);
    }

    @Test
    void fallsBackWhenLlmReturnsInvalidJson() {
        LlmIntentParser parser = parser(FakeLlmClient.available("not json"));

        ParsedIntent intent = parser.parse("随机推荐定数12.7到13.4的5首歌");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RANDOM_RECOMMENDATION);
        assertThat(intent.getCount()).isEqualTo(5);
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.RULE);
    }

    @Test
    void fallsBackWhenLlmReturnsUnknownIntentValue() {
        LlmIntentParser parser = parser(FakeLlmClient.available("{\"intent\":\"DROP_TABLE\"}"));

        ParsedIntent intent = parser.parse("最近20条成绩");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RECENT_SCORES);
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.RULE);
    }

    @Test
    void fallsBackWhenLlmRequestFails() {
        LlmIntentParser parser = parser(FakeLlmClient.failing());

        ParsedIntent intent = parser.parse("最近20条成绩");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RECENT_SCORES);
        assertThat(intent.getLimit()).isEqualTo(20);
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.RULE);
    }

    @Test
    void fallsBackWhenLlmReturnsUnsupportedField() {
        LlmIntentParser parser = parser(FakeLlmClient.available("{\"intent\":\"RECENT_SCORES\",\"sql\":\"select * from score_record\"}"));

        ParsedIntent intent = parser.parse("最近20条成绩");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RECENT_SCORES);
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.RULE);
    }

    private LlmIntentParser parser(LlmClient llmClient) {
        return new LlmIntentParser(llmClient, new RuleBasedIntentParser(), new ObjectMapper());
    }

    private static class FakeLlmClient implements LlmClient {
        private final boolean available;
        private final String response;
        private final boolean fail;

        private FakeLlmClient(boolean available, String response, boolean fail) {
            this.available = available;
            this.response = response;
            this.fail = fail;
        }

        static FakeLlmClient unavailable() {
            return new FakeLlmClient(false, null, false);
        }

        static FakeLlmClient available(String response) {
            return new FakeLlmClient(true, response, false);
        }

        static FakeLlmClient failing() {
            return new FakeLlmClient(true, null, true);
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String chat(String systemPrompt, String userContent) {
            if (fail) {
                throw new IllegalStateException("timeout");
            }
            return response;
        }
    }
}
