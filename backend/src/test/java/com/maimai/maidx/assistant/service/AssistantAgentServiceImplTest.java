package com.maimai.maidx.assistant.service;

import com.maimai.maidx.assistant.agent.AssistantAgentPlan;
import com.maimai.maidx.assistant.agent.AssistantAgentPlanner;
import com.maimai.maidx.assistant.agent.AssistantAgentToolCall;
import com.maimai.maidx.assistant.agent.AssistantAnswerGenerator;
import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.context.UserIdentitySource;
import com.maimai.maidx.assistant.dto.AssistantConversationContext;
import com.maimai.maidx.assistant.dto.AssistantQueryRequest;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.AssistantStructuredData;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.enums.ParserSource;
import com.maimai.maidx.assistant.tool.AssistantToolNames;
import com.maimai.maidx.assistant.tool.AssistantToolRegistry;
import com.maimai.maidx.assistant.tool.ToolResult;
import com.maimai.maidx.entity.User;
import com.maimai.maidx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantAgentServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private AssistantAgentPlanner planner;
    @Mock private AssistantToolRegistry toolRegistry;
    @Mock private AssistantAnswerGenerator answerGenerator;

    private AssistantAgentServiceImpl service;
    private final AssistantUserContext context =
            new AssistantUserContext(7L, UserIdentitySource.DEVELOPMENT_HEADER);

    @BeforeEach
    void setUp() {
        service = new AssistantAgentServiceImpl(
                userRepository, planner, toolRegistry, answerGenerator);
    }

    @Test
    void executesOneToolAndReturnsToolInfoAndStructuredData() {
        User user = new User();
        user.setId(7L);
        when(userRepository.selectById(7L)).thenReturn(user);
        AssistantAgentToolCall call = new AssistantAgentToolCall(
                AssistantToolNames.RECENT_SCORES, Map.of("limit", 5));
        when(planner.plan("最近5条", AssistantConversationContext.empty()))
                .thenReturn(AssistantAgentPlan.single(call, ParserSource.LLM));
        AssistantStructuredData data = new AssistantStructuredData();
        data.getScores().add(new AssistantQueryResponse.ScoreItem());
        ToolResult toolResult = ToolResult.success(
                AssistantToolNames.RECENT_SCORES,
                IntentType.RECENT_SCORES,
                "已查询最近1条成绩。",
                data);
        when(toolRegistry.execute(AssistantToolNames.RECENT_SCORES, context, Map.of("limit", 5)))
                .thenReturn(toolResult);
        when(answerGenerator.generate(toolResult)).thenReturn("已为你找到最近1条成绩。");

        AssistantQueryResponse response = service.query(context, request("最近5条"));

        assertThat(response.getAnswer()).isEqualTo("已为你找到最近1条成绩。");
        assertThat(response.getTool().getName()).isEqualTo(AssistantToolNames.RECENT_SCORES);
        assertThat(response.getTool().getPlannerSource()).isEqualTo(ParserSource.LLM);
        assertThat(response.getStructuredData().getScores()).hasSize(1);
        assertThat(response.getScores()).hasSize(1);
        verify(toolRegistry).execute(AssistantToolNames.RECENT_SCORES, context, Map.of("limit", 5));
    }

    @Test
    void returnsUnsupportedWithoutExecutingTool() {
        User user = new User();
        user.setId(7L);
        when(userRepository.selectById(7L)).thenReturn(user);
        when(planner.plan("今天天气", AssistantConversationContext.empty()))
                .thenReturn(AssistantAgentPlan.unsupported(ParserSource.RULE));

        AssistantQueryResponse response = service.query(context, request("今天天气"));

        assertThat(response.getIntent()).isEqualTo(IntentType.UNKNOWN);
        assertThat(response.getTool()).isNull();
    }

    @Test
    void rejectsPlanThatExceedsSingleToolPolicy() {
        User user = new User();
        user.setId(7L);
        when(userRepository.selectById(7L)).thenReturn(user);
        when(planner.plan("组合查询", AssistantConversationContext.empty())).thenReturn(
                new AssistantAgentPlan(List.of(
                        new AssistantAgentToolCall(AssistantToolNames.RECENT_SCORES, Map.of()),
                        new AssistantAgentToolCall(AssistantToolNames.TOP_SCORES, Map.of())), ParserSource.LLM));

        assertThatThrownBy(() -> service.query(context, request("组合查询")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多允许1个Tool");
    }

    @Test
    void rejectsMissingUserBeforePlanning() {
        when(userRepository.selectById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.query(context, request("最近成绩")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在: 7");
    }

    private AssistantQueryRequest request(String message) {
        AssistantQueryRequest request = new AssistantQueryRequest();
        request.setMessage(message);
        return request;
    }
}
