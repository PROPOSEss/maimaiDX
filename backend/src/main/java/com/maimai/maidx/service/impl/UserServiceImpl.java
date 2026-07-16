package com.maimai.maidx.service.impl;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.maimai.maidx.config.JwtProperties;
import com.maimai.maidx.config.WeChatProperties;
import com.maimai.maidx.entity.User;
import com.maimai.maidx.repository.UserRepository;
import com.maimai.maidx.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserRepository, User> implements UserService {

    private final WeChatProperties weChatProperties;
    private final JwtProperties jwtProperties;

    @Override
    public User loginByWechat(String code) {
        // 1. 调用微信接口获取openid
        String url = String.format(
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
            weChatProperties.getAppid(), weChatProperties.getSecret(), code
        );

        String response = HttpUtil.get(url);
        JSONObject json = JSON.parseObject(response);
        String openid = json.getString("openid");
        String sessionKey = json.getString("session_key");

        if (openid == null) {
            throw new RuntimeException("微信登录失败: " + json.getString("errmsg"));
        }

        // 2. 查询或创建用户
        User user = getByOpenid(openid);
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setSessionKey(sessionKey);
            user.setStatus(1);
            save(user);
            log.info("新用户注册: openid={}", openid);
        } else {
            user.setSessionKey(sessionKey);
            updateById(user);
        }

        return user;
    }

    @Override
    public User getByOpenid(String openid) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
    }

    /**
     * 生成JWT Token
     */
    public String generateToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("openid", user.getOpenid())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(key)
                .compact();
    }
}
