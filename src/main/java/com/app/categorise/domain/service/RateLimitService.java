package com.app.categorise.domain.service;

import com.app.categorise.domain.model.RateLimitConfig;
import com.app.categorise.domain.model.RateLimitResult;

import java.util.UUID;

/**
 * RateLimitService - Domain service interface for rate limiting functionality.
 *
 * Rate limits are derived from the user's subscription tier by default.
 * Per-user overrides can be set via {@link #setUserOverride} for special cases
 * (e.g. beta testers, custom deals). When no override exists, tier defaults apply.
 */
public interface RateLimitService {

    /**
     * Check if a user can make a transcription request based on all rate limits.
     * Checks total quota (from actual transcripts), daily burst, and per-minute burst.
     * @param userId The user ID to check
     * @return RateLimitResult indicating if request is allowed and relevant metadata
     */
    RateLimitResult checkRateLimit(UUID userId);

    /**
     * Record a transcription for burst-limit tracking (minute and day windows).
     * @param userId The user ID who made the transcription
     */
    void recordTranscription(UUID userId);

    /**
     * Get the effective rate limit configuration for a user.
     * Returns the per-user override if one exists, otherwise tier defaults.
     * @param userId The user ID
     * @return RateLimitConfig containing the user's effective limits
     */
    RateLimitConfig getEffectiveLimits(UUID userId);

    /**
     * Set a per-user rate limit override. Use for special cases only —
     * most users should rely on tier defaults derived from their subscription.
     * @param userId The user ID
     * @param config The override configuration
     */
    void setUserOverride(UUID userId, RateLimitConfig config);

    /**
     * Remove a per-user rate limit override, reverting to tier defaults.
     * @param userId The user ID
     */
    void removeUserOverride(UUID userId);

    /**
     * Check if a user has a per-user rate limit override set.
     * @param userId The user ID
     * @return true if user has an override, false if using tier defaults
     */
    boolean hasUserOverride(UUID userId);
}