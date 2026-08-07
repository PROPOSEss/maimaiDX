package com.maimai.maidx.assistant.parser;

import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.enums.ParserSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedIntentParserTest {

    private final RuleBasedIntentParser parser = new RuleBasedIntentParser();

    @Test
    void parsesRecentScores() {
        ParsedIntent intent = parser.parse("查询最近20条成绩");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RECENT_SCORES);
        assertThat(intent.getLimit()).isEqualTo(20);
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.RULE);
    }

    @Test
    void parsesTopScores() {
        ParsedIntent intent = parser.parse("看看最高30张成绩");

        assertThat(intent.getIntent()).isEqualTo(IntentType.TOP_SCORES);
        assertThat(intent.getLimit()).isEqualTo(30);
    }

    @Test
    void parsesRandomRecommendation() {
        ParsedIntent intent = parser.parse("随机推荐定数12.7到13.4的5首歌");

        assertThat(intent.getIntent()).isEqualTo(IntentType.RANDOM_RECOMMENDATION);
        assertThat(intent.getCount()).isEqualTo(5);
        assertThat(intent.getMinConstant()).isEqualByComparingTo(new BigDecimal("12.7"));
        assertThat(intent.getMaxConstant()).isEqualByComparingTo(new BigDecimal("13.4"));
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.RULE);
    }

    @Test
    void parsesTrainingAdviceBeforeRecentScores() {
        ParsedIntent intent = parser.parse("根据我最近的成绩给我3条训练建议");

        assertThat(intent.getIntent()).isEqualTo(IntentType.TRAINING_ADVICE);
        assertThat(intent.getAdviceCount()).isEqualTo(3);
        assertThat(intent.getParserSource()).isEqualTo(ParserSource.RULE);
    }

    @Test
    void leavesAdviceCountEmptyWhenUserDoesNotSpecifyIt() {
        ParsedIntent intent = parser.parse("根据我最近的成绩给一些训练建议");

        assertThat(intent.getIntent()).isEqualTo(IntentType.TRAINING_ADVICE);
        assertThat(intent.getAdviceCount()).isNull();
    }

    @Test
    void returnsUnknownForUnsupportedMessage() {
        ParsedIntent intent = parser.parse("今天吃什么");

        assertThat(intent.getIntent()).isEqualTo(IntentType.UNKNOWN);
    }
}
