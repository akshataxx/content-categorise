package com.app.categorise.domain.service;

import com.app.categorise.api.dto.auth.AppleAuthRequest;
import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.repository.UserRepository;
import com.app.categorise.security.jwt.JwtTokenProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SubscriptionService subscriptionService;

    private AppleAuthService appleAuthService;

    @BeforeEach
    void setUp() {
        appleAuthService = new AppleAuthService();
        ReflectionTestUtils.setField(appleAuthService, "userRepository", userRepository);
        ReflectionTestUtils.setField(appleAuthService, "jwtTokenProvider", jwtTokenProvider);
        ReflectionTestUtils.setField(appleAuthService, "refreshTokenService", refreshTokenService);
        ReflectionTestUtils.setField(appleAuthService, "subscriptionService", subscriptionService);
        ReflectionTestUtils.setField(appleAuthService, "jwtRefreshExpirationInMs", 86_400_000L);
        ReflectionTestUtils.setField(appleAuthService, "appleSignInAudience", "com.example.app");
    }

    @Test
    void authenticateVerifiedAppleToken_rejectsMismatchedRequestUserIdentifier() {
        AppleAuthRequest request = new AppleAuthRequest(
                "signed.jwt",
                "request-user",
                "victim@example.com",
                "Eve",
                "Mallory"
        );

        SignedJWT token = tokenWithClaims("token-user", "com.example.app", null);

        assertThrows(SecurityException.class,
                () -> appleAuthService.authenticateVerifiedAppleToken(request, token));
        verifyNoInteractions(userRepository);
    }

    @Test
    void authenticateVerifiedAppleToken_rejectsWrongAudience() {
        AppleAuthRequest request = new AppleAuthRequest(
                "signed.jwt",
                "apple-user",
                "victim@example.com",
                "Eve",
                "Mallory"
        );

        SignedJWT token = tokenWithClaims("apple-user", "wrong.audience", "victim@example.com");

        assertThrows(SecurityException.class,
                () -> appleAuthService.authenticateVerifiedAppleToken(request, token));
        verifyNoInteractions(userRepository);
    }

    @Test
    void authenticateVerifiedAppleToken_doesNotLinkByRequestEmailWhenTokenEmailIsMissing() throws Exception {
        AppleAuthRequest request = new AppleAuthRequest(
                "signed.jwt",
                "apple-user",
                "victim@example.com",
                "Eve",
                "Mallory"
        );
        SignedJWT token = tokenWithClaims("apple-user", "com.example.app", null);

        when(userRepository.findByAppleUserId("apple-user")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(jwtTokenProvider.generateToken(any())).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh");

        appleAuthService.authenticateVerifiedAppleToken(request, token);

        verify(userRepository, never()).findByEmail(anyString());
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        assertEquals("apple-user", saved.getValue().getAppleUserId());
        assertNull(saved.getValue().getEmail());
    }

    @Test
    void authenticateVerifiedAppleToken_linksExistingUserByVerifiedTokenEmailOnly() throws Exception {
        AppleAuthRequest request = new AppleAuthRequest(
                "signed.jwt",
                "apple-user",
                "attacker-supplied@example.com",
                "Alice",
                "Smith"
        );
        SignedJWT token = tokenWithClaims("apple-user", "com.example.app", "real-user@example.com");

        UserEntity existing = new UserEntity();
        existing.setId(UUID.randomUUID());
        existing.setEmail("real-user@example.com");

        when(userRepository.findByAppleUserId("apple-user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("real-user@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(jwtTokenProvider.generateToken(any())).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh");

        assertDoesNotThrow(() -> appleAuthService.authenticateVerifiedAppleToken(request, token));

        verify(userRepository).findByEmail("real-user@example.com");
        verify(userRepository, never()).findByEmail("attacker-supplied@example.com");
    }

    private SignedJWT tokenWithClaims(String subject, String audience, String email) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("https://appleid.apple.com")
                .audience(audience)
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));

        if (email != null) {
            claims.claim("email", email);
        }

        return new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
    }
}
