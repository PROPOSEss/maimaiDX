package com.maimai.maidx.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 登录响应
 */
@Data
public class LoginResponse {

    private String token;
    private Long userId;
    private String nickname;
    private String avatarUrl;

    /** 是否已绑定MAID */
    private Boolean bound;

    /** 绑定信息（可选） */
    private BindInfo bindInfo;

    @Data
    public static class BindInfo {
        private Long playerId;
        private String maId;
        private String playerName;
        private Integer rating;
    }
}
