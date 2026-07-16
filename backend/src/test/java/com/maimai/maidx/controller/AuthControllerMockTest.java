package com.maimai.maidx.controller;

import com.maimai.maidx.service.PlayerBindService;
import com.maimai.maidx.service.UserService;
import com.maimai.maidx.utils.JwtUtil;
import com.maimai.maidx.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerMockTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PlayerBindService playerBindService;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(userService, jwtUtil, playerBindService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void mockLoginReturnsTokenAndUserInfo() throws Exception {
        when(jwtUtil.generateToken(eq(999L), eq("mock-openid-999")))
                .thenReturn("mock-jwt-token");

        mockMvc.perform(post("/auth/mock-login")
                        .param("username", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.data.userId").value(999))
                .andExpect(jsonPath("$.data.nickname").value("tester"))
                .andExpect(jsonPath("$.data.bound").value(false));
    }

    @Test
    void wechatLoginReturnsFrontendContract() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setOpenid("openid-7");
        user.setNickname("wechat-user");
        user.setAvatar("avatar-url");

        when(userService.loginByWechat("wechat-code")).thenReturn(user);
        when(jwtUtil.generateToken(7L, "openid-7")).thenReturn("wechat-token");
        when(playerBindService.getByUserId(7L)).thenReturn(null);

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"wechat-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("wechat-token"))
                .andExpect(jsonPath("$.data.avatarUrl").value("avatar-url"))
                .andExpect(jsonPath("$.data.bound").value(false));
    }
}
