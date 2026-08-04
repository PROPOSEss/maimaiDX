package com.maimai.maidx.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataSanitizerTest {

    @Test
    void redactsCommonSecretsAndConnectionStrings() {
        String sanitized = SensitiveDataSanitizer.sanitize(
                "password=abc123 Authorization=Bearer token123 jdbc:mysql://localhost:3306/db?password=secret apiKey=sk-test");

        assertThat(sanitized)
                .doesNotContain("abc123")
                .doesNotContain("token123")
                .doesNotContain("localhost:3306")
                .doesNotContain("sk-test")
                .contains("password=******")
                .contains("Authorization=******");
    }
}
