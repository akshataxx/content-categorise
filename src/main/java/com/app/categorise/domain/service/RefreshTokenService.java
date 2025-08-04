package com.app.categorise.domain.service;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

interface RefreshTokenService {
    @Transactional
    void save(UUID userId, String refreshToken, Instant expiry);
    boolean isValid(String refreshToken);

    @Transactional
    void deleteByUserId(UUID userId);
}
