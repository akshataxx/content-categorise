package com.app.categorise.security.jwt;

import com.app.categorise.domain.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String JWT_SECRET =
            "test-secret-that-is-long-enough-for-hs512-signatures-and-remains-deterministic-123456789";

    private JwtTokenProvider provider;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 86_400_000);
        ReflectionTestUtils.setField(provider, "jwtRefreshExpirationInMs", 7_776_000_000L);
        provider.init();

        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", null);
        authentication = new UsernamePasswordAuthenticationToken(user, null, null);
    }

    @Test
    void generatedAccessToken_IsAcceptedOnlyAsAccessToken() {
        String token = provider.generateToken(authentication);

        assertThat(provider.validateAccessToken(token)).isTrue();
        assertThat(provider.validateRefreshToken(token)).isFalse();
    }

    @Test
    void generatedRefreshToken_IsAcceptedOnlyAsRefreshToken() {
        String token = provider.generateRefreshToken(authentication);

        assertThat(provider.validateRefreshToken(token)).isTrue();
        assertThat(provider.validateAccessToken(token)).isFalse();
    }

    @Test
    void legacyTokenWithoutPurpose_IsRejectedForBothUses() {
        String token = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusSeconds(3_600)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
                .compact();

        assertThat(provider.validateAccessToken(token)).isFalse();
        assertThat(provider.validateRefreshToken(token)).isFalse();
    }
}
