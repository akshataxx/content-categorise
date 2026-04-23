package com.app.categorise.application.internal;

import com.app.categorise.application.mapper.RateLimitMapper;
import com.app.categorise.data.entity.UserRateLimitEntity;
import com.app.categorise.data.entity.UserRateLimitTrackingEntity;
import com.app.categorise.data.repository.UserRateLimitRepository;
import com.app.categorise.data.repository.UserRateLimitTrackingRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.domain.model.RateLimitConfig;
import com.app.categorise.domain.model.RateLimitResult;
import com.app.categorise.domain.service.RateLimitService;
import com.app.categorise.domain.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * RateLimitServiceImpl - Implementation of rate limiting business logic.
 *
 * Limits are derived from the user's subscription tier by default:
 * - Free tier:    5/min, 100/day, 30 total transcripts
 * - Premium tier: 5/min, 100/day, 10000 total transcripts
 *
 * Per-user overrides (stored in user_rate_limits table) take precedence
 * when they exist, for special cases like beta testers or custom deals.
 */
@Service
@Transactional
public class RateLimitServiceImpl implements RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitServiceImpl.class);

    private final UserRateLimitRepository rateLimitRepository;
    private final UserRateLimitTrackingRepository trackingRepository;
    private final UserTranscriptRepository transcriptRepository;
    private final RateLimitMapper mapper;
    private final SubscriptionService subscriptionService;

    // Free-tier defaults
    static final int DEFAULT_TRANSCRIPTS_PER_MINUTE = 5;
    static final int DEFAULT_TRANSCRIPTS_PER_DAY = 100;
    static final int DEFAULT_TOTAL_TRANSCRIPTS = 30;

    // Premium-tier defaults
    static final int PREMIUM_TRANSCRIPTS_PER_MINUTE = 5;
    static final int PREMIUM_TRANSCRIPTS_PER_DAY = 100;
    static final int PREMIUM_TOTAL_TRANSCRIPTS = 10000;

    public RateLimitServiceImpl(UserRateLimitRepository rateLimitRepository,
       UserRateLimitTrackingRepository trackingRepository,
       UserTranscriptRepository transcriptRepository,
       RateLimitMapper mapper,
       SubscriptionService subscriptionService
    ) {
        this.rateLimitRepository = rateLimitRepository;
        this.trackingRepository = trackingRepository;
        this.transcriptRepository = transcriptRepository;
        this.mapper = mapper;
        this.subscriptionService = subscriptionService;
    }

    @Override
    @Transactional(readOnly = true)
    public RateLimitResult checkRateLimit(UUID userId) {
        logger.debug("Checking rate limits for user: {}", userId);

        // Resolve effective limits: override row > tier defaults
        RateLimitConfig config = getEffectiveLimits(userId);

        // Check total transcript limit first (most restrictive for free users)
        RateLimitResult totalLimitResult = checkTotalTranscriptLimit(userId, config);
        if (!totalLimitResult.isAllowed()) {
            logger.info("User {} exceeded total transcript limit", userId);
            return totalLimitResult;
        }

        // Check daily limit
        RateLimitResult dailyLimitResult = checkDailyLimit(userId, config);
        if (!dailyLimitResult.isAllowed()) {
            logger.info("User {} exceeded daily transcript limit", userId);
            return dailyLimitResult;
        }

        // Check per-minute limit
        RateLimitResult minuteLimitResult = checkMinuteLimit(userId, config);
        if (!minuteLimitResult.isAllowed()) {
            logger.info("User {} exceeded per-minute transcript limit", userId);
            return minuteLimitResult;
        }

        logger.debug("Rate limit check passed for user: {}", userId);
        return minuteLimitResult; // Return the most restrictive allowed result
    }

    @Override
    public void recordTranscription(UUID userId) {
        logger.debug("Recording transcription for user: {}", userId);

        Instant now = Instant.now();

        // Record for minute window
        recordUsageForWindow(userId, UserRateLimitTrackingEntity.WindowType.MINUTE,
                           truncateToMinute(now));

        // Record for day window
        recordUsageForWindow(userId, UserRateLimitTrackingEntity.WindowType.DAY,
                           truncateToDay(now));

        logger.debug("Transcription recorded for user: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public RateLimitConfig getEffectiveLimits(UUID userId) {
        // Check for per-user override first
        Optional<UserRateLimitEntity> override = rateLimitRepository.findByUserId(userId);

        if (override.isPresent()) {
            logger.debug("Using per-user override limits for user {}", userId);
            return mapper.toDomainModel(override.get());
        }

        // No override — derive from subscription tier
        boolean isPremium = subscriptionService.hasActivePremiumSubscription(userId);
        return tierDefaults(userId, isPremium);
    }

    @Override
    public void setUserOverride(UUID userId, RateLimitConfig config) {
        logger.info("Setting rate limit override for user: {}", userId);

        Optional<UserRateLimitEntity> existing = rateLimitRepository.findByUserId(userId);

        if (existing.isPresent()) {
            UserRateLimitEntity entity = existing.get();
            mapper.updateEntity(entity, config);
            rateLimitRepository.save(entity);
        } else {
            UserRateLimitEntity newEntity = mapper.toEntity(config);
            if (newEntity.getId() == null) {
                newEntity.setId(UUID.randomUUID());
            }
            rateLimitRepository.save(newEntity);
        }

        logger.info("Rate limit override set for user: {}", userId);
    }

    @Override
    public void removeUserOverride(UUID userId) {
        logger.info("Removing rate limit override for user: {}", userId);
        rateLimitRepository.findByUserId(userId).ifPresent(rateLimitRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserOverride(UUID userId) {
        return rateLimitRepository.existsByUserId(userId);
    }


    private RateLimitResult checkTotalTranscriptLimit(UUID userId, RateLimitConfig config) {
        long totalTranscripts = transcriptRepository.countByUserId(userId);
        int limit = config.getTotalTranscriptsLimit();

        if (totalTranscripts >= limit) {
            return RateLimitResult.denied(
                String.format("Total transcript limit exceeded (%d/%d)", totalTranscripts, limit),
                RateLimitResult.RateLimitType.TOTAL
            );
        }

        int remaining = (int) (limit - totalTranscripts);
        return RateLimitResult.allowed(remaining, null, RateLimitResult.RateLimitType.TOTAL);
    }

    private RateLimitResult checkDailyLimit(UUID userId, RateLimitConfig config) {
        Instant dayStart = truncateToDay(Instant.now());
        int currentCount = getCurrentCount(userId, UserRateLimitTrackingEntity.WindowType.DAY, dayStart);
        int limit = config.getTranscriptsPerDayLimit();

        if (currentCount >= limit) {
            Instant resetTime = dayStart.plus(1, ChronoUnit.DAYS);
            return RateLimitResult.denied(
                String.format("Daily transcript limit exceeded (%d/%d)", currentCount, limit),
                RateLimitResult.RateLimitType.PER_DAY,
                resetTime
            );
        }

        int remaining = limit - currentCount;
        Instant resetTime = dayStart.plus(1, ChronoUnit.DAYS);
        return RateLimitResult.allowed(remaining, resetTime, RateLimitResult.RateLimitType.PER_DAY);
    }

    private RateLimitResult checkMinuteLimit(UUID userId, RateLimitConfig config) {
        Instant minuteStart = truncateToMinute(Instant.now());
        int currentCount = getCurrentCount(userId, UserRateLimitTrackingEntity.WindowType.MINUTE, minuteStart);
        int limit = config.getTranscriptsPerMinuteLimit();

        if (currentCount >= limit) {
            Instant resetTime = minuteStart.plus(1, ChronoUnit.MINUTES);
            return RateLimitResult.denied(
                String.format("Per-minute transcript limit exceeded (%d/%d)", currentCount, limit),
                RateLimitResult.RateLimitType.PER_MINUTE,
                resetTime
            );
        }

        int remaining = limit - currentCount;
        Instant resetTime = minuteStart.plus(1, ChronoUnit.MINUTES);
        return RateLimitResult.allowed(remaining, resetTime, RateLimitResult.RateLimitType.PER_MINUTE);
    }

    private int getCurrentCount(UUID userId, UserRateLimitTrackingEntity.WindowType windowType, Instant windowStart) {
        return trackingRepository.findByUserIdAndWindowStartAndWindowType(userId, windowStart, windowType)
                .map(UserRateLimitTrackingEntity::getRequestCount)
                .orElse(0);
    }

    private void recordUsageForWindow(UUID userId, UserRateLimitTrackingEntity.WindowType windowType, Instant windowStart) {
        // Try to increment existing record
        int rowsUpdated = trackingRepository.incrementRequestCount(userId, windowStart, windowType);

        if (rowsUpdated == 0) {
            // No existing record, create new one with count = 1
            UserRateLimitTrackingEntity newRecord = new UserRateLimitTrackingEntity(userId, windowStart, windowType, 1);
            trackingRepository.save(newRecord);
        }
    }

    private Instant truncateToMinute(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    private Instant truncateToDay(Instant instant) {
        return instant.truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * Build a RateLimitConfig from tier defaults (no database row needed).
     */
    private RateLimitConfig tierDefaults(UUID userId, boolean isPremium) {
        return new RateLimitConfig(
            null,
            userId,
            isPremium ? PREMIUM_TRANSCRIPTS_PER_MINUTE : DEFAULT_TRANSCRIPTS_PER_MINUTE,
            isPremium ? PREMIUM_TRANSCRIPTS_PER_DAY : DEFAULT_TRANSCRIPTS_PER_DAY,
            isPremium ? PREMIUM_TOTAL_TRANSCRIPTS : DEFAULT_TOTAL_TRANSCRIPTS,
            null,
            null
        );
    }
}
