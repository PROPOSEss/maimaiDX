package com.maimai.maidx.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 微信小程序 API 调用服务
 *
 * 用于 code2Session 获取用户 OpenID
 */
public class WxMaService {

    private static final Logger log = LoggerFactory.getLogger(WxMaService.class);
    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    private final String appId;
    private final String appSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WxMaService(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 通过微信 code 换取 OpenID
     *
     * @param code 微信小程序登录 code
     * @return 包含 openid 和 session_key 的 Map，失败时返回 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> code2Session(String code) {
        String url = String.format(CODE2SESSION_URL, appId, appSecret, code);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            Map<String, Object> result = objectMapper.readValue(body, Map.class);

            if (result.containsKey("openid")) {
                log.info("微信登录成功, openid={}", result.get("openid"));
                return result;
            } else {
                log.warn("微信登录失败: {}", result.get("errmsg"));
                return null;
            }
        } catch (Exception e) {
            log.error("调用微信 code2Session 异常", e);
            return null;
        }
    }
}
