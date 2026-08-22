package com.app.categorise.domain.service;

import com.app.categorise.data.entity.RefreshTokenEntity;
import com.app.categorise.data.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository repo;

    public RefreshTokenServiceImpl(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void save(UUID userId, String refreshToken, Instant expiry) {
        RefreshTokenEntity existingToken = repo.findByToken(refreshToken).orElse(null);
        if (existingToken != null) {
            existingToken.setExpiryDate(expiry);
            repo.save(existingToken);
            return;
        }

        repo.deleteByUserId(userId);
        repo.save(new RefreshTokenEntity(null, userId, refreshToken, expiry));
    }

    @Override
    public boolean isValid(String refreshToken) {
        return repo.findByToken(refreshToken)
                .filter(rt -> rt.getExpiryDate().isAfter(Instant.now()))
                .isPresent();
    }

    @Override
    @Transactional
    public void revoke(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            repo.deleteByToken(refreshToken);
        }
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        repo.deleteByUserId(userId);
    }
}
