package com.maimai.maidx.assistant.context;

import com.maimai.maidx.assistant.config.AssistantProperties;
import com.maimai.maidx.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantUserContextResolverTest {

    @Test
    void developmentUsesHeaderThenDefault() {
        AssistantProperties properties = new AssistantProperties();
        properties.setDefaultUserId(999L);
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        AssistantUserContextResolver resolver = new AssistantUserContextResolver(
                properties, mock(JwtUtil.class), environment);

        assertThat(resolver.resolve(7L, null).userId()).isEqualTo(7L);
        assertThat(resolver.resolve(null, null).userId()).isEqualTo(999L);
    }

    @Test
    void productionIgnoresDevelopmentHeaderAndUsesJwt() {
        AssistantProperties properties = new AssistantProperties();
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("valid-token")).thenReturn(42L);
        AssistantUserContextResolver resolver = new AssistantUserContextResolver(
                properties, jwtUtil, environment);

        AssistantUserContext context = resolver.resolve(7L, "Bearer valid-token");

        assertThat(context.userId()).isEqualTo(42L);
        assertThat(context.source()).isEqualTo(UserIdentitySource.AUTHENTICATION);
        assertThatThrownBy(() -> resolver.resolve(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bearer");
    }
}
