package com.app.categorise.data.repository;

import com.app.categorise.data.entity.BaseTranscriptEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("BaseTranscriptRepository Integration Tests")
class BaseTranscriptRepositoryTest {

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
    private BaseTranscriptRepository baseTranscriptRepository;

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicOperations {

        @Test
        @DisplayName("Should save and retrieve base transcript")
        void shouldSaveAndFindBaseTranscript() {
            // Given
            BaseTranscriptEntity transcript = new BaseTranscriptEntity(
                    "https://example.com/video1",
                    "This is a test transcript content",
                    "Test video description",
                    "Test Video Title",
                    120.5,
                    Instant.now().minusSeconds(3600),
                    "test_account_id",
                    "test_account",
                    "test_identifier_id",
                    "test_identifier"
            );

            // When
            BaseTranscriptEntity saved = baseTranscriptRepository.save(transcript);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getVideoUrl()).isEqualTo("https://example.com/video1");
            assertThat(saved.getTranscript()).isEqualTo("This is a test transcript content");
            assertThat(saved.getTitle()).isEqualTo("Test Video Title");
            assertThat(saved.getDuration()).isEqualTo(120.5);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getTranscribedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("Should find transcript by video URL")
        void shouldFindByVideoUrl() {
            // Given
            String videoUrl = "https://example.com/video2";
            BaseTranscriptEntity transcript = new BaseTranscriptEntity(
                    videoUrl,
                    "Another test transcript",
                    "Another description",
                    "Another Title",
                    90.0,
                    Instant.now().minusSeconds(1800),
                    "account2",
                    "account_name2",
                    "identifier2",
                    "identifier_name2"
            );
            baseTranscriptRepository.save(transcript);

            // When
            Optional<BaseTranscriptEntity> found = baseTranscriptRepository.findByVideoUrl(videoUrl);

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().getVideoUrl()).isEqualTo(videoUrl);
            assertThat(found.get().getTranscript()).isEqualTo("Another test transcript");
        }

        @Test
        @DisplayName("Should return empty when video URL not found")
        void shouldReturnEmptyWhenVideoUrlNotFound() {
            // When
            Optional<BaseTranscriptEntity> found = baseTranscriptRepository.findByVideoUrl("https://nonexistent.com/video");

            // Then
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("Should check existence by video URL")
        void shouldCheckExistenceByVideoUrl() {
            // Given
            String videoUrl = "https://example.com/video3";
            BaseTranscriptEntity transcript = new BaseTranscriptEntity(
                    videoUrl,
                    "Existence test transcript",
                    "Existence description",
                    "Existence Title",
                    60.0,
                    Instant.now(),
                    "account3",
                    "account_name3",
                    "identifier3",
                    "identifier_name3"
            );
            baseTranscriptRepository.save(transcript);

            // When & Then
            assertThat(baseTranscriptRepository.existsByVideoUrl(videoUrl)).isTrue();
            assertThat(baseTranscriptRepository.existsByVideoUrl("https://nonexistent.com/video")).isFalse();
        }
    }

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("Should enforce unique constraint on video URL")
        void shouldEnforceUniqueVideoUrl() {
            // Given
            String videoUrl = "https://example.com/duplicate-video";
            BaseTranscriptEntity transcript1 = new BaseTranscriptEntity(
                    videoUrl,
                    "First transcript",
                    "First description",
                    "First Title",
                    100.0,
                    Instant.now(),
                    "account1",
                    "account_name1",
                    "identifier1",
                    "identifier_name1"
            );
            baseTranscriptRepository.save(transcript1);

            BaseTranscriptEntity transcript2 = new BaseTranscriptEntity(
                    videoUrl, // Same URL
                    "Second transcript",
                    "Second description",
                    "Second Title",
                    200.0,
                    Instant.now(),
                    "account2",
                    "account_name2",
                    "identifier2",
                    "identifier_name2"
            );

            // When & Then
            assertThat(baseTranscriptRepository.existsByVideoUrl(videoUrl)).isTrue();

            // Attempting to save duplicate should fail due to unique constraint
            assertThrows(DataIntegrityViolationException.class, () -> {
                baseTranscriptRepository.save(transcript2);
                baseTranscriptRepository.flush(); // Force the constraint check
            }, "Should have thrown DataIntegrityViolationException for duplicate video URL");
        }

        @Test
        @DisplayName("Should handle nullable fields correctly")
        void shouldHandleNullableFields() {
            // Given - transcript with minimal required fields
            BaseTranscriptEntity transcript = new BaseTranscriptEntity();
            transcript.setVideoUrl("https://example.com/minimal-video");
            transcript.setTranscript("Minimal transcript content");
            // Leave other fields null

            // When
            BaseTranscriptEntity saved = baseTranscriptRepository.save(transcript);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getVideoUrl()).isEqualTo("https://example.com/minimal-video");
            assertThat(saved.getTranscript()).isEqualTo("Minimal transcript content");
            assertThat(saved.getDescription()).isNull();
            assertThat(saved.getTitle()).isNull();
            assertThat(saved.getDuration()).isNull();
            assertThat(saved.getUploadedAt()).isNull();
            assertThat(saved.getAccountId()).isNull();
            assertThat(saved.getAccount()).isNull();
            assertThat(saved.getIdentifierId()).isNull();
            assertThat(saved.getIdentifier()).isNull();
            assertThat(saved.getCreatedAt()).isNotNull(); // Should be auto-set
            assertThat(saved.getTranscribedAt()).isNotNull(); // Should be auto-set
        }
    }
}