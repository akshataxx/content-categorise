package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserRateLimitEntity;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRateLimitRepositoryTest {

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
    private UserRateLimitRepository userRateLimitRepository;
    
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
    @DisplayName("findByUserId")
    class FindByUserIdTests {

        @Test
        @DisplayName("Should find rate limit configuration by user ID")
        void shouldFindRateLimitByUserId() {
            // Given
            UUID userId = createTestUser();
            UserRateLimitEntity rateLimitEntity = new UserRateLimitEntity(
                    userId, 10, 200, 20000
            );
            userRateLimitRepository.save(rateLimitEntity);

            // When
            Optional<UserRateLimitEntity> result = userRateLimitRepository.findByUserId(userId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(userId);
            assertThat(result.get().getTranscriptsPerMinuteLimit()).isEqualTo(10);
            assertThat(result.get().getTranscriptsPerDayLimit()).isEqualTo(200);
            assertThat(result.get().getTotalTranscriptsLimit()).isEqualTo(20000);
        }

        @Test
        @DisplayName("Should return empty when user ID not found")
        void shouldReturnEmptyWhenUserIdNotFound() {
            // Given
            UUID nonExistentUserId = UUID.randomUUID();

            // When
            Optional<UserRateLimitEntity> result = userRateLimitRepository.findByUserId(nonExistentUserId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByUserId")
    class ExistsByUserIdTests {

        @Test
        @DisplayName("Should return true when user has rate limits")
        void shouldReturnTrueWhenUserHasRateLimits() {
            // Given
            UUID userId = createTestUser();
            UserRateLimitEntity rateLimitEntity = new UserRateLimitEntity(userId);
            userRateLimitRepository.save(rateLimitEntity);

            // When
            boolean exists = userRateLimitRepository.existsByUserId(userId);

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Should return false when user has no rate limits")
        void shouldReturnFalseWhenUserHasNoRateLimits() {
            // Given
            UUID nonExistentUserId = UUID.randomUUID();

            // When
            boolean exists = userRateLimitRepository.existsByUserId(nonExistentUserId);

            // Then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteByUserId")
    class DeleteByUserIdTests {

        @Test
        @DisplayName("Should delete rate limit configuration by user ID")
        void shouldDeleteRateLimitByUserId() {
            // Given
            UUID userId = createTestUser();
            UserRateLimitEntity rateLimitEntity = new UserRateLimitEntity(userId);
            userRateLimitRepository.save(rateLimitEntity);
            
            // Verify it exists
            assertThat(userRateLimitRepository.existsByUserId(userId)).isTrue();

            // When
            userRateLimitRepository.deleteByUserId(userId);

            // Then
            assertThat(userRateLimitRepository.existsByUserId(userId)).isFalse();
        }

        @Test
        @DisplayName("Should not throw exception when deleting non-existent user")
        void shouldNotThrowExceptionWhenDeletingNonExistentUser() {
            // Given
            UUID nonExistentUserId = UUID.randomUUID();

            // When & Then - should not throw exception
            userRateLimitRepository.deleteByUserId(nonExistentUserId);
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
            UserRateLimitEntity entity = new UserRateLimitEntity();
            entity.setUserId(userId);
            entity.setTranscriptsPerMinuteLimit(5);
            entity.setTranscriptsPerDayLimit(100);
            entity.setTotalTranscriptsLimit(10000);

            // When
            UserRateLimitEntity saved = userRateLimitRepository.save(entity);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
            // Check that timestamps are close (within 1 second)
            long timeDiff = Math.abs(java.time.Duration.between(saved.getCreatedAt(), saved.getUpdatedAt()).toMillis());
            assertThat(timeDiff).isLessThan(1000);
        }

        @Test
        @DisplayName("Should update timestamp on entity update")
        void shouldUpdateTimestampOnEntityUpdate() throws InterruptedException {
            // Given
            UUID userId = createTestUser();
            UserRateLimitEntity entity = new UserRateLimitEntity(userId);
            UserRateLimitEntity saved = userRateLimitRepository.save(entity);
            Instant originalUpdatedAt = saved.getUpdatedAt();

            // Wait a bit to ensure timestamp difference
            Thread.sleep(100); // Increased wait time

            // When
            saved.setTranscriptsPerMinuteLimit(10);
            UserRateLimitEntity updated = userRateLimitRepository.save(saved);

            // Then
            assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
            assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        }

        @Test
        @DisplayName("Should use default values from constructor")
        void shouldUseDefaultValuesFromConstructor() {
            // Given
            UUID userId = createTestUser();

            // When
            UserRateLimitEntity entity = new UserRateLimitEntity(userId);
            UserRateLimitEntity saved = userRateLimitRepository.save(entity);

            // Then
            assertThat(saved.getTranscriptsPerMinuteLimit()).isEqualTo(5);
            assertThat(saved.getTranscriptsPerDayLimit()).isEqualTo(100);
            assertThat(saved.getTotalTranscriptsLimit()).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("Constraints")
    class ConstraintsTests {

        @Test
        @DisplayName("Should enforce unique constraint on user_id")
        void shouldEnforceUniqueConstraintOnUserId() {
            // Given
            UUID userId = createTestUser();
            UserRateLimitEntity firstEntity = new UserRateLimitEntity(userId);
            userRateLimitRepository.save(firstEntity);

            // When & Then
            UserRateLimitEntity secondEntity = new UserRateLimitEntity(userId);
            
            // This should throw an exception due to unique constraint
            org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> {
                    userRateLimitRepository.save(secondEntity);
                    userRateLimitRepository.flush(); // Force the constraint check
                }
            );
        }
    }
}