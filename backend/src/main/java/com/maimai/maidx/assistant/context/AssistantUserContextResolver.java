package com.maimai.maidx.assistant.context;

import com.maimai.maidx.assistant.config.AssistantProperties;
import com.maimai.maidx.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AssistantUserContextResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AssistantProperties properties;
    private final JwtUtil jwtUtil;
    private final Environment environment;

    public AssistantUserContext resolve(Long developmentHeaderUserId, String authorizationHeader) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            return resolveProductionContext(authorizationHeader);
        }
        Long userId = developmentHeaderUserId != null
                ? developmentHeaderUserId
                : properties.getDefaultUserId();
        UserIdentitySource source = developmentHeaderUserId != null
                ? UserIdentitySource.DEVELOPMENT_HEADER
                : UserIdentitySource.DEVELOPMENT_DEFAULT;
        return new AssistantUserContext(userId, source);
    }

    private AssistantUserContext resolveProductionContext(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("生产环境必须提供Bearer认证信息");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
            throw new IllegalArgumentException("认证信息无效或已过期");
        }
        return new AssistantUserContext(
                jwtUtil.getUserIdFromToken(token),
                UserIdentitySource.AUTHENTICATION);
    }
}
