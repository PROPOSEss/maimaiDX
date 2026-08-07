package com.maimai.maidx.assistant.dto;

import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.enums.ParserSource;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ParsedIntent {

    private IntentType intent = IntentType.UNKNOWN;

    private Integer limit;

    private Integer count;

    private Integer adviceCount;

    private BigDecimal minConstant;

    private BigDecimal maxConstant;

    private ParserSource parserSource = ParserSource.RULE;

    public static ParsedIntent unknown() {
        ParsedIntent parsedIntent = new ParsedIntent();
        parsedIntent.setIntent(IntentType.UNKNOWN);
        parsedIntent.setParserSource(ParserSource.RULE);
        return parsedIntent;
    }
}
