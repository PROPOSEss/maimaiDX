package com.maimai.maidx.assistant.context;

import java.util.Objects;

/**
 * Trusted user identity resolved by the backend. Tool arguments must never provide this value.
 */
public record AssistantUserContext(Long userId, UserIdentitySource source) {

    public AssistantUserContext {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(source, "source");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId 必须大于 0");
        }
    }
}
