package com.maimai.maidx.assistant.service;

import com.maimai.maidx.assistant.agent.AssistantAgentPlan;
import com.maimai.maidx.assistant.agent.AssistantAgentPlanner;
import com.maimai.maidx.assistant.agent.AssistantAgentToolCall;
import com.maimai.maidx.assistant.agent.AssistantAnswerGenerator;
import com.maimai.maidx.assistant.context.AssistantUserContext;
import com.maimai.maidx.assistant.dto.AssistantConversationContext;
import com.maimai.maidx.assistant.dto.AssistantQueryRequest;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.AssistantStructuredData;
import com.maimai.maidx.assistant.dto.AssistantToolInfo;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.enums.ParserSource;
import com.maimai.maidx.assistant.tool.AssistantToolNames;
import com.maimai.maidx.assistant.tool.AssistantToolRegistry;
import com.maimai.maidx.assistant.tool.ToolResult;
import com.maimai.maidx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantAgentServiceImpl implements AssistantAgentService {

    private static final int CURRENT_MAX_TOOL_CALLS = 1;

    private final UserRepository userRepository;
    private final AssistantAgentPlanner planner;
    private final AssistantToolRegistry toolRegistry;
    private final AssistantAnswerGenerator answerGenerator;

    @Override
    public AssistantQueryResponse query(AssistantUserContext userContext,
                                        AssistantQueryRequest request,
                                        AssistantConversationContext conversationContext) {
        ensureUserExists(userContext.userId());
        AssistantConversationContext safeConversation = conversationContext == null
                ? AssistantConversationContext.empty()
                : conversationContext;
        AssistantAgentPlan plan = planner.plan(request.getMessage(), safeConversation);
        if (plan.toolCalls().size() > CURRENT_MAX_TOOL_CALLS) {
            throw new IllegalArgumentException("Agent单次请求最多允许" + CURRENT_MAX_TOOL_CALLS + "个Tool");
        }
        if (plan.toolCalls().isEmpty()) {
            return unsupported(userContext, plan);
        }

        AssistantAgentToolCall call = plan.toolCalls().get(0);
        log.info("Assistant Agent执行Tool: userId={}, tool={}, source={}",
                userContext.userId(), call.toolName(), plan.source());
        ToolResult result = toolRegistry.execute(call.toolName(), userContext, call.arguments());

        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(userContext.userId());
        response.setIntent(result.intent());
        response.setParserSource(plan.source());
        response.setAdviceSource(result.adviceSource());
        response.setParsedIntent(toParsedIntent(call, result.intent(), plan.source()));
        response.setAnswer(answerGenerator.generate(result));
        response.setTool(toolInfo(call, plan));
        applyStructuredData(response, result.data());
        return response;
    }

    private void ensureUserExists(Long userId) {
        if (userRepository.selectById(userId) == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
    }

    private AssistantQueryResponse unsupported(AssistantUserContext userContext, AssistantAgentPlan plan) {
        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(userContext.userId());
        response.setIntent(IntentType.UNKNOWN);
        response.setParserSource(plan.source());
        response.setParsedIntent(ParsedIntent.unknown());
        response.getParsedIntent().setParserSource(plan.source());
        response.setAnswer("暂时只支持查询最近成绩、最高成绩、随机推荐、训练建议和歌曲搜索。");
        return response;
    }

    private AssistantToolInfo toolInfo(AssistantAgentToolCall call, AssistantAgentPlan plan) {
        AssistantToolInfo info = new AssistantToolInfo();
        info.setName(call.toolName());
        info.setPlannerSource(plan.source());
        info.setSuccess(true);
        info.setArguments(call.arguments());
        return info;
    }

    private void applyStructuredData(AssistantQueryResponse response, AssistantStructuredData data) {
        AssistantStructuredData safeData = data == null ? new AssistantStructuredData() : data;
        response.setStructuredData(safeData);
        response.setScores(safeData.getScores());
        response.setRecommendations(safeData.getRecommendations());
        response.setSuggestions(safeData.getSuggestions());
        response.setEvidence(safeData.getEvidence());
        response.setSongs(safeData.getSongs());
    }

    private ParsedIntent toParsedIntent(AssistantAgentToolCall call,
                                        IntentType intent,
                                        ParserSource source) {
        Map<String, Object> args = call.arguments();
        ParsedIntent parsed = new ParsedIntent();
        parsed.setIntent(intent);
        parsed.setParserSource(source);
        if (AssistantToolNames.RECENT_SCORES.equals(call.toolName())
                || AssistantToolNames.TOP_SCORES.equals(call.toolName())) {
            parsed.setLimit(intOrNull(args.get("limit")));
        } else if (AssistantToolNames.RANDOM_RECOMMENDATION.equals(call.toolName())) {
            parsed.setCount(intOrNull(args.get("count")));
            parsed.setMinConstant(decimalOrNull(args.get("minConstant")));
            parsed.setMaxConstant(decimalOrNull(args.get("maxConstant")));
        } else if (AssistantToolNames.TRAINING_ADVICE.equals(call.toolName())) {
            parsed.setAdviceCount(intOrNull(args.get("adviceCount")));
        } else if (AssistantToolNames.SONG_SEARCH.equals(call.toolName())) {
            parsed.setKeyword(args.get("keyword") == null ? null : args.get("keyword").toString());
            parsed.setLimit(intOrNull(args.get("limit")));
        }
        return parsed;
    }

    private Integer intOrNull(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private BigDecimal decimalOrNull(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}
