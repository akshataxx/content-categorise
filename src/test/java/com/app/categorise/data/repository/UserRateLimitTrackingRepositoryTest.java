package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserRateLimitTrackingEntity;
import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRateLimitTrackingRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRateLimitTrackingRepository trackingRepository;
    
    @Autowired
    private UserRepository userRepository;

    // Helper method to create a user for testing
    private UUID createTestUser() {
        UserEntity user = new UserEntity();
        user.setSub("test-sub-" + System.nanoTime());
        user.setEmail("test-" + System.nanoTime() + "@example.com");
        UserEntity saved = userRepository.saveAndFlush(user);
        return saved.getId();
    }

    @Nested
    @DisplayName("findByUserIdAndWindowStartAndWindowType")
    class FindByUserIdAndWindowStartAndWindowTypeTests {

        @Test
        @DisplayName("Should find tracking record by user ID, window start, and window type")
        void shouldFindTrackingRecord() {
            // Given
            UUID userId = createTestUser();
            Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            UserRateLimitTrackingEntity entity = new UserRateLimitTrackingEntity(
                    userId, windowStart, UserRateLimitTrackingEntity.WindowType.MINUTE, 3
            );
            trackingRepository.save(entity);

            // When
            Optional<UserRateLimitTrackingEntity> result = trackingRepository
                    .findByUserIdAndWindowStartAndWindowType(userId, windowStart, 
                            UserRateLimitTrackingEntity.WindowType.MINUTE);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(userId);
            assertThat(result.get().getWindowStart()).isEqualTo(windowStart);
            assertThat(result.get().getWindowType()).isEqualTo(UserRateLimitTrackingEntity.WindowType.MINUTE);
            assertThat(result.get().getRequestCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return empty when no matching record found")
        void shouldReturnEmptyWhenNoMatchingRecord() {
            // Given
            UUID userId = createTestUser();
            Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);

            // When
            Optional<UserRateLimitTrackingEntity> result = trackingRepository
                    .findByUserIdAndWindowStartAndWindowType(userId, windowStart, 
                            UserRateLimitTrackingEntity.WindowType.MINUTE);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should distinguish between different window types")
        void shouldDistinguishBetweenWindowTypes() {
            // Given
            UUID userId = createTestUser();
            Instant windowStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
            
            UserRateLimitTrackingEntity minuteEntity = new UserRateLimitTrackingEntity(
                    userId, windowStart, UserRateLimitTrackingEntity.WindowType.MINUTE, 1
            );
            UserRateLimitTrackingEntity dayEntity = new UserRateLimitTrackingEntity(
                    userId, windowStart, UserRateLimitTrackingEntity.WindowType.DAY, 2
            );
            
            trackingRepository.save(minuteEntity);
            trackingRepository.save(dayEntity);

            // When
            Optional<UserRateLimitTrackingEntity> minuteResult = trackingRepository
                    .findByUserIdAndWindowStartAndWindowType(userId, windowStart, 
                            UserRateLimitTrackingEntity.WindowType.MINUTE);
            Optional<UserRateLimitTrackingEntity> dayResult = trackingRepository
                    .findByUserIdAndWindowStartAndWindowType(userId, windowStart, 
                            UserRateLimitTrackingEntity.WindowType.DAY);

            // Then
            assertThat(minuteResult).isPresent();
            assertThat(minuteResult.get().getRequestCount()).isEqualTo(1);
            assertThat(dayResult).isPresent();
            assertThat(dayResult.get().getRequestCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndWindowType")
    class FindByUserIdAndWindowTypeTests {

        @Test
        @DisplayName("Should find all tracking records for user and window type")
        void shouldFindAllTrackingRecordsForUserAndWindowType() {
            // Given
            UUID userId = createTestUser();
            Instant now = Instant.now();
            
            UserRateLimitTrackingEntity entity1 = new UserRateLimitTrackingEntity(
                    userId, now.minus(2, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 1
            );
            UserRateLimitTrackingEntity entity2 = new UserRateLimitTrackingEntity(
                    userId, now.minus(1, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 2
            );
            UserRateLimitTrackingEntity dayEntity = new UserRateLimitTrackingEntity(
                    userId, now.truncatedTo(ChronoUnit.DAYS), 
                    UserRateLimitTrackingEntity.WindowType.DAY, 3
            );
            
            trackingRepository.save(entity1);
            trackingRepository.save(entity2);
            trackingRepository.save(dayEntity);

            // When
            List<UserRateLimitTrackingEntity> minuteResults = trackingRepository
                    .findByUserIdAndWindowType(userId, UserRateLimitTrackingEntity.WindowType.MINUTE);
            List<UserRateLimitTrackingEntity> dayResults = trackingRepository
                    .findByUserIdAndWindowType(userId, UserRateLimitTrackingEntity.WindowType.DAY);

            // Then
            assertThat(minuteResults).hasSize(2);
            assertThat(dayResults).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndWindowTypeAndTimeRange")
    class FindByUserIdAndWindowTypeAndTimeRangeTests {

        @Test
        @DisplayName("Should find tracking records within time range")
        void shouldFindTrackingRecordsWithinTimeRange() {
            // Given
            UUID userId = createTestUser();
            Instant now = Instant.now();
            Instant startTime = now.minus(5, ChronoUnit.MINUTES);
            Instant endTime = now.minus(1, ChronoUnit.MINUTES);
            
            UserRateLimitTrackingEntity beforeRange = new UserRateLimitTrackingEntity(
                    userId, now.minus(10, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 1
            );
            UserRateLimitTrackingEntity inRange1 = new UserRateLimitTrackingEntity(
                    userId, now.minus(3, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 2
            );
            UserRateLimitTrackingEntity inRange2 = new UserRateLimitTrackingEntity(
                    userId, now.minus(2, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 3
            );
            UserRateLimitTrackingEntity afterRange = new UserRateLimitTrackingEntity(
                    userId, now, 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 4
            );
            
            trackingRepository.save(beforeRange);
            trackingRepository.save(inRange1);
            trackingRepository.save(inRange2);
            trackingRepository.save(afterRange);

            // When
            List<UserRateLimitTrackingEntity> results = trackingRepository
                    .findByUserIdAndWindowTypeAndTimeRange(userId, 
                            UserRateLimitTrackingEntity.WindowType.MINUTE, startTime, endTime);

            // Then
            assertThat(results).hasSize(2);
            assertThat(results).extracting(UserRateLimitTrackingEntity::getRequestCount)
                    .containsExactlyInAnyOrder(2, 3);
        }
    }

    @Nested
    @DisplayName("incrementRequestCount")
    class IncrementRequestCountTests {

        @Test
        @Transactional(readOnly = false)
        @DisplayName("Should increment request count for existing record")
        void shouldIncrementRequestCountForExistingRecord() {
            // Given
            UUID userId = createTestUser();
            Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            UserRateLimitTrackingEntity entity = new UserRateLimitTrackingEntity(
                    userId, windowStart, UserRateLimitTrackingEntity.WindowType.MINUTE, 5
            );
            trackingRepository.saveAndFlush(entity); // Ensure entity is persisted before increment

            // When
            int rowsAffected = trackingRepository.incrementRequestCount(userId, windowStart, 
                    UserRateLimitTrackingEntity.WindowType.MINUTE);
            trackingRepository.flush(); // Ensure the update is flushed to database

            // Then
            assertThat(rowsAffected).isEqualTo(1);
            
            Optional<UserRateLimitTrackingEntity> updated = trackingRepository
                    .findByUserIdAndWindowStartAndWindowType(userId, windowStart, 
                            UserRateLimitTrackingEntity.WindowType.MINUTE);
            assertThat(updated).isPresent();
            assertThat(updated.get().getRequestCount()).isEqualTo(6);
        }

        @Test
        @DisplayName("Should return 0 when no record exists to increment")
        void shouldReturn0WhenNoRecordExistsToIncrement() {
            // Given
            UUID userId = createTestUser();
            Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);

            // When
            int rowsAffected = trackingRepository.incrementRequestCount(userId, windowStart, 
                    UserRateLimitTrackingEntity.WindowType.MINUTE);

            // Then
            assertThat(rowsAffected).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("deleteOldTrackingRecords")
    class DeleteOldTrackingRecordsTests {

        @Test
        @DisplayName("Should delete old tracking records before cutoff time")
        void shouldDeleteOldTrackingRecordsBeforeCutoffTime() {
            // Given
            Instant now = Instant.now();
            Instant cutoffTime = now.minus(1, ChronoUnit.HOURS);
            UUID userId = createTestUser();
            
            UserRateLimitTrackingEntity oldRecord = new UserRateLimitTrackingEntity(
                    userId, now.minus(2, ChronoUnit.HOURS), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 1
            );
            UserRateLimitTrackingEntity recentRecord = new UserRateLimitTrackingEntity(
                    userId, now.minus(30, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 2
            );
            
            trackingRepository.save(oldRecord);
            trackingRepository.save(recentRecord);

            // When
            int deletedCount = trackingRepository.deleteOldTrackingRecords(
                    UserRateLimitTrackingEntity.WindowType.MINUTE, cutoffTime);

            // Then
            assertThat(deletedCount).isEqualTo(1);
            
            List<UserRateLimitTrackingEntity> remaining = trackingRepository
                    .findByUserIdAndWindowType(userId, UserRateLimitTrackingEntity.WindowType.MINUTE);
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getRequestCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should only delete records of specified window type")
        void shouldOnlyDeleteRecordsOfSpecifiedWindowType() {
            // Given
            Instant now = Instant.now();
            Instant cutoffTime = now.minus(1, ChronoUnit.HOURS);
            UUID userId = createTestUser();
            
            UserRateLimitTrackingEntity oldMinuteRecord = new UserRateLimitTrackingEntity(
                    userId, now.minus(2, ChronoUnit.HOURS), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 1
            );
            UserRateLimitTrackingEntity oldDayRecord = new UserRateLimitTrackingEntity(
                    userId, now.minus(2, ChronoUnit.HOURS), 
                    UserRateLimitTrackingEntity.WindowType.DAY, 2
            );
            
            trackingRepository.save(oldMinuteRecord);
            trackingRepository.save(oldDayRecord);

            // When
            int deletedCount = trackingRepository.deleteOldTrackingRecords(
                    UserRateLimitTrackingEntity.WindowType.MINUTE, cutoffTime);

            // Then
            assertThat(deletedCount).isEqualTo(1);
            
            List<UserRateLimitTrackingEntity> remainingDay = trackingRepository
                    .findByUserIdAndWindowType(userId, UserRateLimitTrackingEntity.WindowType.DAY);
            assertThat(remainingDay).hasSize(1);
        }
    }

    @Nested
    @DisplayName("deleteByUserId")
    class DeleteByUserIdTests {

        @Test
        @DisplayName("Should delete all tracking records for a user")
        void shouldDeleteAllTrackingRecordsForUser() {
            // Given
            UUID userId1 = createTestUser();
            UUID userId2 = createTestUser();
            Instant now = Instant.now();
            
            UserRateLimitTrackingEntity user1Record1 = new UserRateLimitTrackingEntity(
                    userId1, now.minus(1, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 1
            );
            UserRateLimitTrackingEntity user1Record2 = new UserRateLimitTrackingEntity(
                    userId1, now.truncatedTo(ChronoUnit.DAYS), 
                    UserRateLimitTrackingEntity.WindowType.DAY, 2
            );
            UserRateLimitTrackingEntity user2Record = new UserRateLimitTrackingEntity(
                    userId2, now.minus(1, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 3
            );
            
            trackingRepository.save(user1Record1);
            trackingRepository.save(user1Record2);
            trackingRepository.save(user2Record);

            // When
            trackingRepository.deleteByUserId(userId1);

            // Then
            List<UserRateLimitTrackingEntity> user1Records = trackingRepository
                    .findByUserIdAndWindowType(userId1, UserRateLimitTrackingEntity.WindowType.MINUTE);
            List<UserRateLimitTrackingEntity> user2Records = trackingRepository
                    .findByUserIdAndWindowType(userId2, UserRateLimitTrackingEntity.WindowType.MINUTE);
            
            assertThat(user1Records).isEmpty();
            assertThat(user2Records).hasSize(1);
        }
    }

    @Nested
    @DisplayName("countByWindowType")
    class CountByWindowTypeTests {

        @Test
        @DisplayName("Should count records by window type")
        void shouldCountRecordsByWindowType() {
            // Given
            UUID userId = createTestUser();
            Instant now = Instant.now();
            
            UserRateLimitTrackingEntity minuteRecord1 = new UserRateLimitTrackingEntity(
                    userId, now.minus(1, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 1
            );
            UserRateLimitTrackingEntity minuteRecord2 = new UserRateLimitTrackingEntity(
                    userId, now.minus(2, ChronoUnit.MINUTES), 
                    UserRateLimitTrackingEntity.WindowType.MINUTE, 2
            );
            UserRateLimitTrackingEntity dayRecord = new UserRateLimitTrackingEntity(
                    userId, now.truncatedTo(ChronoUnit.DAYS), 
                    UserRateLimitTrackingEntity.WindowType.DAY, 3
            );
            
            trackingRepository.save(minuteRecord1);
            trackingRepository.save(minuteRecord2);
            trackingRepository.save(dayRecord);

            // When
            long minuteCount = trackingRepository.countByWindowType(UserRateLimitTrackingEntity.WindowType.MINUTE);
            long dayCount = trackingRepository.countByWindowType(UserRateLimitTrackingEntity.WindowType.DAY);

            // Then
            assertThat(minuteCount).isEqualTo(2);
            assertThat(dayCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Entity Lifecycle")
    class EntityLifecycleTests {

        @Test
        @DisplayName("Should auto-generate ID and timestamps on save")
        void shouldAutoGenerateIdAndTimestampsOnSave() {
            // Given
            UUID userId = createTestUser();
            Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            UserRateLimitTrackingEntity entity = new UserRateLimitTrackingEntity();
            entity.setUserId(userId);
            entity.setWindowStart(windowStart);
            entity.setWindowType(UserRateLimitTrackingEntity.WindowType.MINUTE);

            // When
            UserRateLimitTrackingEntity saved = trackingRepository.save(entity);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
            assertThat(saved.getRequestCount()).isEqualTo(0); // Default value
        }

        @Test
        @DisplayName("Should enforce unique constraint on user_id, window_start, window_type")
        void shouldEnforceUniqueConstraint() {
            // Given
            UUID userId = createTestUser();
            Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            
            UserRateLimitTrackingEntity firstEntity = new UserRateLimitTrackingEntity(
                    userId, windowStart, UserRateLimitTrackingEntity.WindowType.MINUTE, 1
            );
            trackingRepository.save(firstEntity);

            // When & Then
            UserRateLimitTrackingEntity secondEntity = new UserRateLimitTrackingEntity(
                    userId, windowStart, UserRateLimitTrackingEntity.WindowType.MINUTE, 2
            );
            
            // This should throw an exception due to unique constraint
            org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> {
                    trackingRepository.save(secondEntity);
                    trackingRepository.flush(); // Force the constraint check
                }
            );
        }
    }
}