package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AssistantToolRegistry {

    private final Map<String, AssistantTool> tools;
    private final ToolSchemaValidator schemaValidator;

    public AssistantToolRegistry(List<AssistantTool> toolList, ToolSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
        Map<String, AssistantTool> registered = new LinkedHashMap<>();
        for (AssistantTool tool : toolList) {
            String name = tool.getDefinition().name();
            if (registered.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("重复的Assistant Tool名称: " + name);
            }
        }
        this.tools = Map.copyOf(registered);
    }

    public List<AssistantToolDefinition> getDefinitions() {
        return tools.values().stream().map(AssistantTool::getDefinition).toList();
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public void validate(String name, Map<String, Object> params) {
        AssistantTool tool = requireTool(name);
        schemaValidator.validate(tool.getDefinition(), params);
    }

    public ToolResult execute(String name,
                              AssistantUserContext userContext,
                              Map<String, Object> params) {
        AssistantTool tool = requireTool(name);
        Map<String, Object> safeParams = params == null ? Map.of() : Map.copyOf(params);
        schemaValidator.validate(tool.getDefinition(), safeParams);
        return tool.execute(userContext, safeParams);
    }

    private AssistantTool requireTool(String name) {
        AssistantTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("不存在的Assistant Tool: " + name);
        }
        return tool;
    }
}
