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
        @DisplayName("Should use default config when user has no rate limits")
        void shouldUseDefaultConfigWhenUserHasNoRateLimits() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());
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
    @DisplayName("getUserRateLimits")
    class GetUserRateLimitsTests {

        @Test
        @DisplayName("Should return user rate limits when they exist")
        void shouldReturnUserRateLimitsWhenTheyExist() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.of(rateLimitEntity));
            when(mapper.toDomainModel(rateLimitEntity)).thenReturn(rateLimitConfig);

            // When
            RateLimitConfig result = rateLimitService.getUserRateLimits(userId);

            // Then
            assertThat(result).isEqualTo(rateLimitConfig);
            verify(mapper).toDomainModel(rateLimitEntity);
        }

        @Test
        @DisplayName("Should return default config when user has no rate limits")
        void shouldReturnDefaultConfigWhenUserHasNoRateLimits() {
            // Given
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());

            // When
            RateLimitConfig result = rateLimitService.getUserRateLimits(userId);

            // Then
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getTranscriptsPerMinuteLimit()).isEqualTo(5);
            assertThat(result.getTranscriptsPerDayLimit()).isEqualTo(100);
            assertThat(result.getTotalTranscriptsLimit()).isEqualTo(3);
            assertThat(result.getId()).isNull(); // Default config has no ID
        }
    }

    @Nested
    @DisplayName("updateUserRateLimits")
    class UpdateUserRateLimitsTests {

        @Test
        @DisplayName("Should update existing rate limits")
        void shouldUpdateExistingRateLimits() {
            // Given
            RateLimitConfig updatedConfig = new RateLimitConfig(
                    rateLimitConfig.getId(),
                    userId,
                    10, 200, 20000,
                    rateLimitConfig.getCreatedAt(),
                    Instant.now()
            );
            
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.of(rateLimitEntity));

            // When
            rateLimitService.updateUserRateLimits(userId, updatedConfig);

            // Then
            verify(mapper).updateEntity(rateLimitEntity, updatedConfig);
            verify(rateLimitRepository).save(rateLimitEntity);
        }

        @Test
        @DisplayName("Should create new rate limits when none exist")
        void shouldCreateNewRateLimitsWhenNoneExist() {
            // Given
            RateLimitConfig newConfig = new RateLimitConfig(
                    null, userId, 10, 200, 20000, null, null
            );
            UserRateLimitEntity newEntity = new UserRateLimitEntity(userId, 10, 200, 20000);
            
            when(rateLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(mapper.toEntity(newConfig)).thenReturn(newEntity);

            // When
            rateLimitService.updateUserRateLimits(userId, newConfig);

            // Then
            verify(mapper).toEntity(newConfig);
            verify(rateLimitRepository).save(newEntity);
        }
    }

    @Nested
    @DisplayName("initializeDefaultLimits")
    class InitializeDefaultLimitsTests {

        @Test
        @DisplayName("Should initialize default limits for new user")
        void shouldInitializeDefaultLimitsForNewUser() {
            // Given
            when(rateLimitRepository.existsByUserId(userId)).thenReturn(false);

            // When
            rateLimitService.initializeDefaultLimits(userId);

            // Then
            verify(rateLimitRepository).save(any(UserRateLimitEntity.class));
        }

        @Test
        @DisplayName("Should not initialize limits when user already has them")
        void shouldNotInitializeLimitsWhenUserAlreadyHasThem() {
            // Given
            when(rateLimitRepository.existsByUserId(userId)).thenReturn(true);

            // When
            rateLimitService.initializeDefaultLimits(userId);

            // Then
            verify(rateLimitRepository, never()).save(any(UserRateLimitEntity.class));
        }
    }

    @Nested
    @DisplayName("hasRateLimits")
    class HasRateLimitsTests {

        @Test
        @DisplayName("Should return true when user has rate limits")
        void shouldReturnTrueWhenUserHasRateLimits() {
            // Given
            when(rateLimitRepository.existsByUserId(userId)).thenReturn(true);

            // When
            boolean result = rateLimitService.hasRateLimits(userId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when user has no rate limits")
        void shouldReturnFalseWhenUserHasNoRateLimits() {
            // Given
            when(rateLimitRepository.existsByUserId(userId)).thenReturn(false);

            // When
            boolean result = rateLimitService.hasRateLimits(userId);

            // Then
            assertThat(result).isFalse();
        }
    }
}