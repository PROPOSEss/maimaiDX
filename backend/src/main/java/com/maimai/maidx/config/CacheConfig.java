package com.maimai.maidx.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig implements CachingConfigurer {

    public static final String SONG_DETAIL_CACHE = "songDetail";
    public static final String SONG_CHARTS_CACHE = "songCharts";

    @Bean
    @Override
    public CacheManager cacheManager() {
        return buildCacheManager(connectionFactory, cacheProperties, redisCacheValueSerializer());
    }

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private CacheProperties cacheProperties;

    private CacheManager buildCacheManager(RedisConnectionFactory connectionFactory,
                                           CacheProperties cacheProperties,
                                           RedisSerializer<Object> redisCacheValueSerializer) {
        log.info("Redis Spring Cache enabled: caches=[{}, {}], songTtl={}",
                SONG_DETAIL_CACHE, SONG_CHARTS_CACHE, cacheProperties.getSongTtl());

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith("maidx:")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisCacheValueSerializer));

        RedisCacheConfiguration songConfig = defaultConfig.entryTtl(cacheProperties.getSongTtl());

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        SONG_DETAIL_CACHE, songConfig,
                        SONG_CHARTS_CACHE, songConfig
                ))
                .build();
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache get failed, fallback to MySQL: cache={}, key={}, error={}, message={}",
                        cache.getName(), key, exception.getClass().getSimpleName(), exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis cache put failed, returning MySQL result: cache={}, key={}, error={}, message={}",
                        cache.getName(), key, exception.getClass().getSimpleName(), exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache evict failed: cache={}, key={}, error={}",
                        cache.getName(), key, exception.getClass().getSimpleName());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis cache clear failed: cache={}, error={}",
                        cache.getName(), exception.getClass().getSimpleName());
            }
        };
    }

    @Bean
    public RedisSerializer<Object> redisCacheValueSerializer() {
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.maimai.maidx.dto.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(mapper, "@class");
        mapper.activateDefaultTypingAsProperty(validator, ObjectMapper.DefaultTyping.NON_FINAL, "@class");
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
