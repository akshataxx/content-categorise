package com.app.categorise.application.internal;

import com.app.categorise.application.mapper.RateLimitMapper;
import com.app.categorise.data.entity.UserRateLimitEntity;
import com.app.categorise.data.entity.UserRateLimitTrackingEntity;
import com.app.categorise.data.repository.UserRateLimitRepository;
import com.app.categorise.data.repository.UserRateLimitTrackingRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.domain.model.RateLimitConfig;
import com.app.categorise.domain.model.RateLimitResult;
import com.app.categorise.domain.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceImplTest {

    @Mock
    private UserRateLimitRepository rateLimitRepository;

    @Mock
    private UserRateLimitTrackingRepository trackingRepository;

    @Mock
    private UserTranscriptRepository transcriptRepository;

    @Mock
    private RateLimitMapper mapper;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private RateLimitServiceImpl rateLimitService;

    private UUID userId;
    private UserRateLimitEntity rateLimitEntity;
    private RateLimitConfig rateLimitConfig;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        
        rateLimitEntity = new UserRateLimitEntity(userId, 5, 100, 10000);
        
        rateLimitConfig = new RateLimitConfig(
                UUID.randomUUID(),
                userId,
                5,  // per minute
                100, // per day
                10000, // total
                Instant.now(),
                Instant.now()
        );
    }

    @Nested
    @DisplayName("checkRateLimit")
    class CheckRateLimitTests {

        @Test
        @DisplayName("Should allow request when all limits are within bounds")
        void shouldAllowRequestWhenAllLimitsWithinBounds() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.of(rateLimitEntity));
            when(mapper.toDomainModel(rateLimitEntity)).thenReturn(rateLimitConfig);
            when(transcriptRepository.countByUserId(userId)).thenReturn(5000L); // Under total limit
            
            // Mock minute window - under limit
            Instant minuteStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            UserRateLimitTrackingEntity minuteTracking = new UserRateLimitTrackingEntity(
                    userId, minuteStart, UserRateLimitTrackingEntity.WindowType.MINUTE, 3
            );
            when(trackingRepository.findByUserIdAndWindowStartAndWindowType(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.MINUTE)))
                    .thenReturn(Optional.of(minuteTracking));
            
            // Mock day window - under limit
            Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
            UserRateLimitTrackingEntity dayTracking = new UserRateLimitTrackingEntity(
                    userId, dayStart, UserRateLimitTrackingEntity.WindowType.DAY, 50
            );
            when(trackingRepository.findByUserIdAndWindowStartAndWindowType(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.DAY)))
                    .thenReturn(Optional.of(dayTracking));

            // When
            RateLimitResult result = rateLimitService.checkRateLimit(userId);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingRequests()).isEqualTo(2); // 5 - 3 = 2 remaining for minute window
            assertThat(result.getLimitType()).isEqualTo(RateLimitResult.RateLimitType.PER_MINUTE);
        }

        @Test
        @DisplayName("Should deny request when total transcript limit exceeded")
        void shouldDenyRequestWhenTotalLimitExceeded() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.of(rateLimitEntity));
            when(mapper.toDomainModel(rateLimitEntity)).thenReturn(rateLimitConfig);
            when(transcriptRepository.countByUserId(userId)).thenReturn(10000L); // At total limit

            // When
            RateLimitResult result = rateLimitService.checkRateLimit(userId);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getReason()).contains("Total transcript limit exceeded");
            assertThat(result.getLimitType()).isEqualTo(RateLimitResult.RateLimitType.TOTAL);
            assertThat(result.getRemainingRequests()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should deny request when daily limit exceeded")
        void shouldDenyRequestWhenDailyLimitExceeded() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.of(rateLimitEntity));
            when(mapper.toDomainModel(rateLimitEntity)).thenReturn(rateLimitConfig);
            when(transcriptRepository.countByUserId(userId)).thenReturn(5000L); // Under total limit
            
            // Mock day window - at limit
            UserRateLimitTrackingEntity dayTracking = new UserRateLimitTrackingEntity(
                    userId, Instant.now().truncatedTo(ChronoUnit.DAYS), 
                    UserRateLimitTrackingEntity.WindowType.DAY, 100
            );
            when(trackingRepository.findByUserIdAndWindowStartAndWindowType(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.DAY)))
                    .thenReturn(Optional.of(dayTracking));

            // When
            RateLimitResult result = rateLimitService.checkRateLimit(userId);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getReason()).contains("Daily transcript limit exceeded");
            assertThat(result.getLimitType()).isEqualTo(RateLimitResult.RateLimitType.PER_DAY);
            assertThat(result.getRemainingRequests()).isEqualTo(0);
            assertThat(result.getResetTime()).isNotNull();
        }

        @Test
        @DisplayName("Should deny request when per-minute limit exceeded")
        void shouldDenyRequestWhenPerMinuteLimitExceeded() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.of(rateLimitEntity));
            when(mapper.toDomainModel(rateLimitEntity)).thenReturn(rateLimitConfig);
            when(transcriptRepository.countByUserId(userId)).thenReturn(5000L); // Under total limit
            
            // Mock minute window - at limit
            UserRateLimitTrackingEntity minuteTracking = new UserRateLimitTrackingEntity(
                    userId, Instant.now().truncatedTo(ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 5
            );
            when(trackingRepository.findByUserIdAndWindowStartAndWindowType(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.MINUTE)))
                    .thenReturn(Optional.of(minuteTracking));
            
            // Mock day window - under limit
            when(trackingRepository.findByUserIdAndWindowStartAndWindowType(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.DAY)))
                    .thenReturn(Optional.empty()); // No day tracking yet

            // When
            RateLimitResult result = rateLimitService.checkRateLimit(userId);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getReason()).contains("Per-minute transcript limit exceeded");
            assertThat(result.getLimitType()).isEqualTo(RateLimitResult.RateLimitType.PER_MINUTE);
            assertThat(result.getRemainingRequests()).isEqualTo(0);
            assertThat(result.getResetTime()).isNotNull();
        }

        @Test
        @DisplayName("Should use free-tier defaults when no override exists and user is not premium")
        void shouldUseFreeTierDefaultsWhenNoOverrideExists() {
            // Given — no override row, not premium
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(subscriptionService.hasActivePremiumSubscription(userId)).thenReturn(false);
            when(transcriptRepository.countByUserId(userId)).thenReturn(0L);
            when(trackingRepository.findByUserIdAndWindowStartAndWindowType(
                    eq(userId), any(Instant.class), any(UserRateLimitTrackingEntity.WindowType.class)))
                    .thenReturn(Optional.empty());

            // When
            RateLimitResult result = rateLimitService.checkRateLimit(userId);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingRequests()).isEqualTo(5); // Default minute limit
            assertThat(result.getLimitType()).isEqualTo(RateLimitResult.RateLimitType.PER_MINUTE);
        }

        @Test
        @DisplayName("Should use premium-tier defaults when no override exists and user is premium")
        void shouldUsePremiumTierDefaultsWhenNoOverrideAndPremium() {
            // Given — no override row, but premium subscriber
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(subscriptionService.hasActivePremiumSubscription(userId)).thenReturn(true);
            when(transcriptRepository.countByUserId(userId)).thenReturn(5000L);
            when(trackingRepository.findByUserIdAndWindowStartAndWindowType(
                    eq(userId), any(Instant.class), any(UserRateLimitTrackingEntity.WindowType.class)))
                    .thenReturn(Optional.empty());

            // When
            RateLimitResult result = rateLimitService.checkRateLimit(userId);

            // Then — premium total limit is 10000, user has 5000, so allowed
            assertThat(result.isAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("recordTranscription")
    class RecordTranscriptionTests {

        @Test
        @DisplayName("Should record transcription for both minute and day windows")
        void shouldRecordTranscriptionForBothWindows() {
            // Given
            when(trackingRepository.incrementRequestCount(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.MINUTE)))
                    .thenReturn(1); // Existing record updated
            when(trackingRepository.incrementRequestCount(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.DAY)))
                    .thenReturn(1); // Existing record updated

            // When
            rateLimitService.recordTranscription(userId);

            // Then
            verify(trackingRepository).incrementRequestCount(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.MINUTE));
            verify(trackingRepository).incrementRequestCount(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.DAY));
            verify(trackingRepository, never()).save(any(UserRateLimitTrackingEntity.class));
        }

        @Test
        @DisplayName("Should create new tracking records when none exist")
        void shouldCreateNewTrackingRecordsWhenNoneExist() {
            // Given
            when(trackingRepository.incrementRequestCount(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.MINUTE)))
                    .thenReturn(0); // No existing record
            when(trackingRepository.incrementRequestCount(
                    eq(userId), any(Instant.class), eq(UserRateLimitTrackingEntity.WindowType.DAY)))
                    .thenReturn(0); // No existing record

            // When
            rateLimitService.recordTranscription(userId);

            // Then
            verify(trackingRepository, times(2)).save(any(UserRateLimitTrackingEntity.class));
        }
    }

    @Nested
    @DisplayName("getEffectiveLimits")
    class GetEffectiveLimitsTests {

        @Test
        @DisplayName("Should return override when one exists")
        void shouldReturnOverrideWhenOneExists() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.of(rateLimitEntity));
            when(mapper.toDomainModel(rateLimitEntity)).thenReturn(rateLimitConfig);

            // When
            RateLimitConfig result = rateLimitService.getEffectiveLimits(userId);

            // Then
            assertThat(result).isEqualTo(rateLimitConfig);
            verify(mapper).toDomainModel(rateLimitEntity);
            verify(subscriptionService, never()).hasActivePremiumSubscription(any());
        }

        @Test
        @DisplayName("Should return free-tier defaults when no override and not premium")
        void shouldReturnFreeTierDefaultsWhenNoOverrideAndNotPremium() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(subscriptionService.hasActivePremiumSubscription(userId)).thenReturn(false);

            // When
            RateLimitConfig result = rateLimitService.getEffectiveLimits(userId);

            // Then
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getTranscriptsPerMinuteLimit()).isEqualTo(RateLimitServiceImpl.DEFAULT_TRANSCRIPTS_PER_MINUTE);
            assertThat(result.getTranscriptsPerDayLimit()).isEqualTo(RateLimitServiceImpl.DEFAULT_TRANSCRIPTS_PER_DAY);
            assertThat(result.getTotalTranscriptsLimit()).isEqualTo(RateLimitServiceImpl.DEFAULT_TOTAL_TRANSCRIPTS);
            assertThat(result.getId()).isNull();
        }

        @Test
        @DisplayName("Should return premium-tier defaults when no override and premium")
        void shouldReturnPremiumTierDefaultsWhenNoOverrideAndPremium() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(subscriptionService.hasActivePremiumSubscription(userId)).thenReturn(true);

            // When
            RateLimitConfig result = rateLimitService.getEffectiveLimits(userId);

            // Then
            assertThat(result.getTotalTranscriptsLimit()).isEqualTo(RateLimitServiceImpl.PREMIUM_TOTAL_TRANSCRIPTS);
        }
    }

    @Nested
    @DisplayName("setUserOverride")
    class SetUserOverrideTests {

        @Test
        @DisplayName("Should update existing override")
        void shouldUpdateExistingOverride() {
            // Given
            RateLimitConfig updatedConfig = new RateLimitConfig(
                    rateLimitConfig.getId(), userId, 10, 200, 20000,
                    rateLimitConfig.getCreatedAt(), Instant.now()
            );
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.of(rateLimitEntity));

            // When
            rateLimitService.setUserOverride(userId, updatedConfig);

            // Then
            verify(mapper).updateEntity(rateLimitEntity, updatedConfig);
            verify(rateLimitRepository).save(rateLimitEntity);
        }

        @Test
        @DisplayName("Should create new override when none exists")
        void shouldCreateNewOverrideWhenNoneExists() {
            // Given
            RateLimitConfig newConfig = new RateLimitConfig(null, userId, 10, 200, 20000, null, null);
            UserRateLimitEntity newEntity = new UserRateLimitEntity(userId, 10, 200, 20000);
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(mapper.toEntity(newConfig)).thenReturn(newEntity);

            // When
            rateLimitService.setUserOverride(userId, newConfig);

            // Then
            verify(mapper).toEntity(newConfig);
            verify(rateLimitRepository).save(newEntity);
        }
    }

    @Nested
    @DisplayName("hasUserOverride")
    class HasUserOverrideTests {

        @Test
        @DisplayName("Should return true when user has an override")
        void shouldReturnTrueWhenUserHasOverride() {
            when(rateLimitRepository.existsByUserId(userId)).thenReturn(true);
            assertThat(rateLimitService.hasUserOverride(userId)).isTrue();
        }

        @Test
        @DisplayName("Should return false when user has no override")
        void shouldReturnFalseWhenUserHasNoOverride() {
            when(rateLimitRepository.existsByUserId(userId)).thenReturn(false);
            assertThat(rateLimitService.hasUserOverride(userId)).isFalse();
        }
    }
}