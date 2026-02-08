package com.app.categorise.api.controller;

import com.app.categorise.api.dto.auth.JwtAuthResponse;
import com.app.categorise.api.dto.auth.LoginRequest;
import com.app.categorise.api.dto.auth.RefreshTokenRequest;
import com.app.categorise.api.dto.auth.RegisterRequest;
import com.app.categorise.domain.service.AppleAuthService;
import com.app.categorise.domain.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private AppleAuthService appleAuthService;

    @Nested
    @DisplayName("/api/auth/register")
    class Register {
        @Test
        void register_success_returns200() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("alice@example.com");
            req.setPassword("StrongPass!123");

            when(authService.register(any())).thenReturn(new JwtAuthResponse("access","refresh"));

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("/api/auth/login")
    class Login {
        @Test
        void login_success_returns200() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("alice@example.com");
            req.setPassword("StrongPass!123");

            when(authService.login(any())).thenReturn(new JwtAuthResponse("access","refresh"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("/api/auth/refresh")
    class Refresh {
        @Test
        void refresh_success_returns200() throws Exception {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("refresh");

            when(authService.refreshAccessToken(any())).thenReturn(new JwtAuthResponse("new-access","refresh"));

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }
    }
}
