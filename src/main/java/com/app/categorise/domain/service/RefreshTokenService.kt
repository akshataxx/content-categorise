package com.app.categorise.domain.service
import java.util.*

interface RefreshTokenService {
    fun void save(UUID userId, String refreshToken, Instant expiry);

     fun boolean isValid(String refreshToken);

    void deleteByUserId(userId: UUID?)


}