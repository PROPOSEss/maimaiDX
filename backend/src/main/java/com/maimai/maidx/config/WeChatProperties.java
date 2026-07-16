package com.maimai.maidx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WeChatProperties {

    private String appid;
    private String secret;
}
