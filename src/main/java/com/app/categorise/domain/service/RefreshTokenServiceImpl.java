package com.app.categorise.domain.service;

import com.app.categorise.data.entity.RefreshTokenEntity;
import com.app.categorise.data.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository repo;

    public RefreshTokenServiceImpl(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    @Override
    public void save(UUID userId, String refreshToken, Instant expiry) {
        repo.deleteByUserId(userId);
        repo.save(new RefreshTokenEntity(null, userId, refreshToken, expiry));
    }

    @Override
    public boolean isValid(String refreshToken) {
        return repo.findByToken(refreshToken).isPresent();
    }

    @Override
    public void deleteByUserId(UUID userId) {
        repo.deleteByUserId(userId);
    }
}
