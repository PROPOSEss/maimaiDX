package com.maimai.maidx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "maidx.cache")
public class CacheProperties {

    private Duration songTtl = Duration.ofHours(6);
}
