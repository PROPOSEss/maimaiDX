package com.maimai.maidx.assistant.dto;

import com.maimai.maidx.assistant.enums.IntentType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ParsedIntent {

    private IntentType intent = IntentType.UNKNOWN;

    private Integer limit;

    private Integer count;

    private BigDecimal minConstant;

    private BigDecimal maxConstant;

    public static ParsedIntent unknown() {
        ParsedIntent parsedIntent = new ParsedIntent();
        parsedIntent.setIntent(IntentType.UNKNOWN);
        return parsedIntent;
    }
}
