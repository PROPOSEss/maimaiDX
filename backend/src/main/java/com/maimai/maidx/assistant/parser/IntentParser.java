package com.maimai.maidx.assistant.parser;

import com.maimai.maidx.assistant.dto.ParsedIntent;

public interface IntentParser {

    ParsedIntent parse(String message);
}
