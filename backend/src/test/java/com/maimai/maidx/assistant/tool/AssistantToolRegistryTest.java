package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.context.UserIdentitySource;
import com.maimai.maidx.assistant.enums.IntentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantToolRegistryTest {

    @Test
    void validatesSchemaBeforeExecutingTool() {
        AssistantTool tool = mock(AssistantTool.class);
        AssistantToolDefinition definition = new AssistantToolDefinition(
                "test_tool", "test", ToolSchemas.object(Map.of(
                "limit", ToolSchemas.integer(1, 10, "limit"))));
        when(tool.getDefinition()).thenReturn(definition);
        AssistantToolRegistry registry = new AssistantToolRegistry(
                List.of(tool), new ToolSchemaValidator());
        AssistantUserContext context =
                new AssistantUserContext(1L, UserIdentitySource.DEVELOPMENT_HEADER);

        registry.execute("test_tool", context, Map.of("limit", 3));

        verify(tool).execute(context, Map.of("limit", 3));
        assertThatThrownBy(() -> registry.execute("test_tool", context, Map.of("userId", 99)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的tool参数");
    }

    @Test
    void rejectsDuplicateToolNames() {
        AssistantTool left = mock(AssistantTool.class);
        AssistantTool right = mock(AssistantTool.class);
        AssistantToolDefinition definition = new AssistantToolDefinition(
                "same_tool", "same", ToolSchemas.object(Map.of()));
        when(left.getDefinition()).thenReturn(definition);
        when(right.getDefinition()).thenReturn(definition);

        assertThatThrownBy(() -> new AssistantToolRegistry(
                List.of(left, right), new ToolSchemaValidator()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }
}
