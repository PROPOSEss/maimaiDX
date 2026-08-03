package com.maimai.maidx.assistant.service;

import com.maimai.maidx.assistant.config.AssistantProperties;
import com.maimai.maidx.assistant.dto.AssistantQueryRequest;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.parser.IntentParser;
import com.maimai.maidx.assistant.tool.AssistantToolRouter;
import com.maimai.maidx.entity.User;
import com.maimai.maidx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceImplTest {

    private AssistantServiceImpl service;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IntentParser intentParser;

    @Mock
    private AssistantToolRouter toolRouter;

    @BeforeEach
    void setUp() {
        AssistantProperties properties = new AssistantProperties();
        properties.setDefaultUserId(999L);
        service = new AssistantServiceImpl(properties, userRepository, intentParser, toolRouter);
    }

    @Test
    void usesDefaultUserWhenHeaderMissing() {
        User user = new User();
        user.setId(999L);
        when(userRepository.selectById(999L)).thenReturn(user);
        ParsedIntent unknown = ParsedIntent.unknown();
        when(intentParser.parse("不知道")).thenReturn(unknown);

        AssistantQueryResponse response = service.query(null, request("不知道"));

        assertThat(response.getUserId()).isEqualTo(999L);
        verify(userRepository).selectById(999L);
    }

    @Test
    void rejectsInvalidUserId() {
        assertThatThrownBy(() -> service.query(0L, request("最近20条成绩")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId 必须大于 0");
    }

    @Test
    void rejectsMissingUser() {
        when(userRepository.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.query(404L, request("最近20条成绩")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在: 404");
    }

    @Test
    void routesKnownIntentAfterNormalization() {
        User user = new User();
        user.setId(7L);
        when(userRepository.selectById(7L)).thenReturn(user);
        ParsedIntent parsed = new ParsedIntent();
        parsed.setIntent(IntentType.RECENT_SCORES);
        parsed.setLimit(99);
        when(intentParser.parse("最近99条成绩")).thenReturn(parsed);
        AssistantQueryResponse routed = new AssistantQueryResponse();
        routed.setUserId(7L);
        routed.setIntent(IntentType.RECENT_SCORES);
        when(toolRouter.route(any(), any())).thenReturn(routed);

        AssistantQueryResponse response = service.query(7L, request("最近99条成绩"));

        assertThat(response.getIntent()).isEqualTo(IntentType.RECENT_SCORES);
        verify(toolRouter).route(7L, parsed);
        assertThat(parsed.getLimit()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidConstantRange() {
        User user = new User();
        user.setId(999L);
        when(userRepository.selectById(999L)).thenReturn(user);
        ParsedIntent parsed = new ParsedIntent();
        parsed.setIntent(IntentType.RANDOM_RECOMMENDATION);
        parsed.setMinConstant(new java.math.BigDecimal("14.0"));
        parsed.setMaxConstant(new java.math.BigDecimal("13.0"));
        when(intentParser.parse("随机推荐定数14到13的5首歌")).thenReturn(parsed);

        assertThatThrownBy(() -> service.query(999L, request("随机推荐定数14到13的5首歌")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minConstant 不能大于 maxConstant");
    }

    private AssistantQueryRequest request(String message) {
        AssistantQueryRequest request = new AssistantQueryRequest();
        request.setMessage(message);
        return request;
    }
}
