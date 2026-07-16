package com.maimai.maidx.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录请求
 */
@Data
public class LoginRequest {

    @NotBlank(message = "微信登录code不能为空")
    private String code;
}
