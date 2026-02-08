package com.app.categorise.domain.service;

import com.app.categorise.domain.model.RateLimitConfig;
import com.app.categorise.domain.model.RateLimitResult;

import java.util.UUID;

/**
 * RateLimitService - Domain service interface for rate limiting functionality
 * Provides methods to check rate limits, record transcriptions, and manage user limits
 */
public interface RateLimitService {

    /**
     * Check if a user can make a transcription request based on all rate limits
     * @param userId The user ID to check
     * @return RateLimitResult indicating if request is allowed and relevant metadata
     */
    RateLimitResult checkRateLimit(UUID userId);

    /**
     * Record a successful transcription for rate limiting tracking
     * This should be called after a transcription is successfully completed
     * @param userId The user ID who made the transcription
     */
    void recordTranscription(UUID userId);

    /**
     * Get the rate limit configuration for a user
     * @param userId The user ID
     * @return RateLimitConfig containing the user's limits, or default if not found
     */
    RateLimitConfig getUserRateLimits(UUID userId);

    /**
     * Update rate limit configuration for a user
     * @param userId The user ID
     * @param config The new rate limit configuration
     */
    void updateUserRateLimits(UUID userId, RateLimitConfig config);

    /**
     * Initialize default rate limits for a new user
     * @param userId The user ID
     */
    void initializeDefaultLimits(UUID userId);

    /**
     * Check if a user has rate limit configuration
     * @param userId The user ID
     * @return true if user has rate limits configured, false otherwise
     */
    boolean hasRateLimits(UUID userId);

    /**
     * Apply premium rate limits for a user (e.g. on subscription upgrade)
     * @param userId The user ID
     */
    void applyPremiumLimits(UUID userId);

    /**
     * Apply free-tier rate limits for a user (e.g. on subscription downgrade)
     * @param userId The user ID
     */
    void applyFreeLimits(UUID userId);
}