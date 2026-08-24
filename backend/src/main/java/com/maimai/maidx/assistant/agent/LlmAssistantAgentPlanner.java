package com.maimai.maidx.assistant.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.dto.AssistantConversationContext;
import com.maimai.maidx.assistant.enums.ParserSource;
import com.maimai.maidx.assistant.llm.LlmClient;
import com.maimai.maidx.assistant.tool.AssistantToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class LlmAssistantAgentPlanner implements AssistantAgentPlanner {

    private static final Set<String> ROOT_FIELDS = Set.of("toolName", "arguments");
    private static final String PROMPT_TEMPLATE = """
            You are the planner for a maimaiDX assistant. Select at most one backend tool.
            Return exactly one JSON object and nothing else. Never return Markdown, SQL or an explanation.
            Required format: {"toolName":"tool_name","arguments":{}}
            If no tool matches, return {"toolName":"NONE","arguments":{}}.
            Only use parameters declared in the selected tool schema.
            Never create, request or return userId, authentication data, SQL, table names or repository names.
            Available tools and strict parameter schemas:
            %s
            """;

    private final LlmClient llmClient;
    private final RuleBasedAssistantAgentPlanner fallbackPlanner;
    private final AssistantToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public AssistantAgentPlan plan(String message, AssistantConversationContext conversationContext) {
        if (!llmClient.isAvailable()) {
            return fallbackPlanner.plan(message, conversationContext);
        }
        try {
            String definitions = objectMapper.writeValueAsString(toolRegistry.getDefinitions());
            String content = llmClient.chat(PROMPT_TEMPLATE.formatted(definitions), message);
            return parse(content);
        } catch (Exception e) {
            log.warn("LLM Agent规划失败，已降级为规则解析: {}", e.getClass().getSimpleName());
            return fallbackPlanner.plan(message, conversationContext);
        }
    }

    private AssistantAgentPlan parse(String content) throws Exception {
        JsonNode root = objectMapper.readTree(content.trim());
        if (!root.isObject()) {
            throw new IllegalArgumentException("agent plan must be a JSON object");
        }
        root.fieldNames().forEachRemaining(field -> {
            if (!ROOT_FIELDS.contains(field)) {
                throw new IllegalArgumentException("unsupported agent plan field: " + field);
            }
        });
        if (!root.path("toolName").isTextual() || !root.path("arguments").isObject()) {
            throw new IllegalArgumentException("invalid agent plan shape");
        }
        String toolName = root.path("toolName").asText();
        if ("NONE".equals(toolName)) {
            return AssistantAgentPlan.unsupported(ParserSource.LLM);
        }
        if (!toolRegistry.contains(toolName)) {
            throw new IllegalArgumentException("unknown tool: " + toolName);
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.path("arguments").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            arguments.put(field.getKey(), objectMapper.treeToValue(field.getValue(), Object.class));
        }
        toolRegistry.validate(toolName, arguments);
        return AssistantAgentPlan.single(
                new AssistantAgentToolCall(toolName, arguments),
                ParserSource.LLM);
    }
}
