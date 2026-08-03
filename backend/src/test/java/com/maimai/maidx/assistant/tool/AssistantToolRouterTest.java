package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantToolRouterTest {

    @Test
    void routesToMatchingTool() {
        AssistantTool tool = mock(AssistantTool.class);
        when(tool.supportIntent()).thenReturn(IntentType.RECENT_SCORES);
        AssistantQueryResponse expected = new AssistantQueryResponse();
        expected.setIntent(IntentType.RECENT_SCORES);
        ParsedIntent intent = new ParsedIntent();
        intent.setIntent(IntentType.RECENT_SCORES);
        when(tool.execute(999L, intent)).thenReturn(expected);
        AssistantToolRouter router = new AssistantToolRouter(List.of(tool));

        AssistantQueryResponse response = router.route(999L, intent);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void rejectsUnsupportedIntent() {
        AssistantToolRouter router = new AssistantToolRouter(List.of());
        ParsedIntent intent = new ParsedIntent();
        intent.setIntent(IntentType.UNKNOWN);

        assertThatThrownBy(() -> router.route(999L, intent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("暂不支持的助手意图");
    }
}
