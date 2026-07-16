package com.maimai.maidx.controller;

import com.maimai.maidx.dto.LoginRequest;
import com.maimai.maidx.dto.LoginResponse;
import com.maimai.maidx.dto.Result;
import com.maimai.maidx.entity.PlayerBind;
import com.maimai.maidx.entity.User;
import com.maimai.maidx.service.PlayerBindService;
import com.maimai.maidx.service.UserService;
import com.maimai.maidx.utils.BeanUtil;
import com.maimai.maidx.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器 - 支持微信登录和本地 Mock 登录
 */
@Tag(name = "认证管理", description = "微信登录与用户认证（本地 Mock 测试）")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PlayerBindService playerBindService;

    public AuthController(UserService userService, JwtUtil jwtUtil, PlayerBindService playerBindService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.playerBindService = playerBindService;
    }

    @Operation(summary = "微信登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.loginByWechat(request.getCode());
        String token = jwtUtil.generateToken(user.getId(), user.getOpenid());
        PlayerBind bind = playerBindService.getByUserId(user.getId());
        return Result.success(BeanUtil.toLoginResponse(user, token, bind));
    }

    /**
     * Mock 登录接口 - 本地测试用。
     * 不依赖微信，直接生成测试用户和 JWT。
     */
    @Operation(summary = "本地 Mock 登录")
    @PostMapping("/mock-login")
    public Result<LoginResponse> mockLogin(@RequestParam(required = false) String username) {
        User user = new User();
        user.setId(999L);
        user.setOpenid("mock-openid-999");
        user.setNickname(username != null ? username : "测试用户");
        user.setAvatar("https://example.com/avatar.png");

        String token = jwtUtil.generateToken(user.getId(), user.getOpenid());
        PlayerBind bind = null;
        LoginResponse response = BeanUtil.toLoginResponse(user, token, bind);

        return Result.success(response);
    }
}