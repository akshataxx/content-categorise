package com.app.categorise.domain.service;

import com.app.categorise.api.dto.auth.JwtAuthResponse;
import com.app.categorise.api.dto.auth.LoginRequest;
import com.app.categorise.api.dto.auth.RefreshTokenRequest;
import com.app.categorise.api.dto.auth.RegisterRequest;
import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.repository.UserRepository;
import com.app.categorise.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        passwordEncoder = new BCryptPasswordEncoder(12);
        // Use reflection to inject passwordEncoder and jwtRefreshExpirationInMs
        java.lang.reflect.Field pe = AuthService.class.getDeclaredField("passwordEncoder");
        pe.setAccessible(true);
        pe.set(authService, passwordEncoder);

        java.lang.reflect.Field refreshMs = AuthService.class.getDeclaredField("jwtRefreshExpirationInMs");
        refreshMs.setAccessible(true);
        refreshMs.set(authService, 86_400_000L); // 1 day
    }

    @Nested
    class RegisterTests {
        @Test
        void register_Success_HashesPasswordAndReturnsTokens() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("alice@example.com");
            req.setPassword("StrongPass!123");
            req.setFirstName("Alice");
            req.setLastName("Doe");

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            when(userRepository.save(captor.capture())).thenAnswer(inv -> {
                UserEntity u = captor.getValue();
                // simulate id assigned
                java.lang.reflect.Field idField;
                try {
                    idField = UserEntity.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(u, UUID.randomUUID());
                } catch (Exception ignored) { }
                return u;
            });

            when(tokenProvider.generateToken(any())).thenReturn("access");
            when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh");

            JwtAuthResponse res = authService.register(req);

            assertEquals("access", res.getAccessToken());
            assertEquals("refresh", res.getRefreshToken());

            UserEntity saved = captor.getValue();
            assertEquals("alice@example.com", saved.getEmail());
            assertNotNull(saved.getPasswordHash());
            assertTrue(passwordEncoder.matches("StrongPass!123", saved.getPasswordHash()));

            verify(refreshTokenService).save(any(UUID.class), eq("refresh"), any(Instant.class));
        }

        

        @Test
        void register_DuplicateEmail_Throws() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("alice@example.com");
            req.setPassword("StrongPass!123");

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(new UserEntity()));

            assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        }
    }

    @Nested
    class LoginTests {
        @Test
        void login_Success_ReturnsTokens() {
            LoginRequest req = new LoginRequest();
            req.setEmail("alice@example.com");
            req.setPassword("StrongPass!123");

            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            user.setEmail("alice@example.com");
            user.setName("Alice Doe");
            user.setPasswordHash(passwordEncoder.encode("StrongPass!123"));

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
            when(tokenProvider.generateToken(any())).thenReturn("access");
            when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh");

            JwtAuthResponse res = authService.login(req);
            assertEquals("access", res.getAccessToken());
            assertEquals("refresh", res.getRefreshToken());
            verify(refreshTokenService).save(eq(user.getId()), eq("refresh"), any(Instant.class));
        }

        @Test
        void login_WrongPassword_ThrowsBadCredentials() {
            LoginRequest req = new LoginRequest();
            req.setEmail("alice@example.com");
            req.setPassword("Wrong");

            UserEntity user = new UserEntity();
            user.setEmail("alice@example.com");
            user.setPasswordHash(passwordEncoder.encode("StrongPass!123"));

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

            assertThrows(BadCredentialsException.class, () -> authService.login(req));
        }

        @Test
        void login_UserNotFound_ThrowsBadCredentials() {
            LoginRequest req = new LoginRequest();
            req.setEmail("unknown@example.com");
            req.setPassword("whatever");

            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThrows(BadCredentialsException.class, () -> authService.login(req));
        }
    }

    @Nested
    class RefreshTests {
        @Test
        void refresh_Success_IssuesNewAccessToken() throws Exception {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("refresh");

            UUID uid = UUID.randomUUID();
            when(refreshTokenService.isValid("refresh")).thenReturn(true);
            when(tokenProvider.getUserIdFromJWT("refresh")).thenReturn(uid.toString());

            UserEntity ue = new UserEntity();
            ue.setId(uid);
            ue.setName("Alice");
            ue.setEmail("alice@example.com");
            when(userRepository.findById(uid)).thenReturn(Optional.of(ue));

            when(tokenProvider.generateToken(any())).thenReturn("new-access");

            JwtAuthResponse res = authService.refreshAccessToken(req);
            assertEquals("new-access", res.getAccessToken());
            assertEquals("refresh", res.getRefreshToken());
        }
    }
}
