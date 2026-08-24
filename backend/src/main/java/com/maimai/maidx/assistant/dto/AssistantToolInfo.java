package com.maimai.maidx.assistant.dto;

import com.maimai.maidx.assistant.enums.ParserSource;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AssistantToolInfo {

    private String name;

    private ParserSource plannerSource;

    private boolean success;

    private Map<String, Object> arguments = new LinkedHashMap<>();
}
