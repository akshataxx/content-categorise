package com.app.categorise.domain.service;

import com.app.categorise.data.entity.RefreshTokenEntity;
import com.app.categorise.data.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@Import(RefreshTokenServiceImpl.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenServiceImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void addProductionTokenConstraint() {
        jdbcTemplate.execute("alter table refresh_tokens add constraint refresh_tokens_token_key unique (token)");
    }

    @Test
    void save_ExtendsExpiryWhenRefreshTokenAlreadyExists() {
        UUID userId = UUID.randomUUID();
        String token = "existing-refresh-token";
        Instant originalExpiry = Instant.parse("2026-08-23T00:00:00Z");
        Instant extendedExpiry = Instant.parse("2026-09-22T00:00:00Z");

        refreshTokenService.save(userId, token, originalExpiry);
        refreshTokenRepository.flush();

        refreshTokenService.save(userId, token, extendedExpiry);
        refreshTokenRepository.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .extracting(RefreshTokenEntity::getUserId,
                        RefreshTokenEntity::getToken,
                        RefreshTokenEntity::getExpiryDate)
                .containsExactly(userId, token, extendedExpiry);
    }

    @Test
    void revoke_DeletesOnlyThePresentedTokenAndIsIdempotent() {
        String presentedToken = "presented-refresh-token";
        String otherToken = "other-refresh-token";
        Instant expiry = Instant.parse("2026-09-22T00:00:00Z");
        refreshTokenService.save(UUID.randomUUID(), presentedToken, expiry);
        refreshTokenService.save(UUID.randomUUID(), otherToken, expiry);
        refreshTokenRepository.flush();

        refreshTokenService.revoke(presentedToken);
        refreshTokenService.revoke(presentedToken);
        refreshTokenRepository.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findByToken(presentedToken)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(otherToken)).isPresent();
    }
}
