package com.app.categorise.domain.service;

import java.time.Instant;
import java.util.UUID;

interface RefreshTokenService {
    void save(UUID userId, String refreshToken, Instant expiry);
    boolean isValid(String refreshToken);

    void deleteByUserId(UUID userId);
}
