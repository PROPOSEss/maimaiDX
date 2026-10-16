package com.maimai.maidx.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides an explicit cache-disabled mode for repeatable MySQL-path verification.
 *
 * <p>The configuration intentionally does not enable Spring's caching proxy, so
 * {@code @Cacheable} methods execute normally and reach the database. Production
 * and development startup keep using Redis unless {@code spring.cache.type=none}
 * is set explicitly.</p>
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "none")
public class NoOpCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new NoOpCacheManager();
    }
}
