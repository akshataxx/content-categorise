package com.app.categorise.data.repository;

import com.app.categorise.data.entity.BaseTranscriptEntity;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

    @Test
    void shouldSaveAndFindBaseTranscript() {
        // Given
        BaseTranscriptEntity transcript = new BaseTranscriptEntity(
                "https://example.com/video1",
                "This is a test transcript content",
                null, // structuredContent
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

    @Test
    void shouldFindByVideoUrl() {
        // Given
        String videoUrl = "https://example.com/video2";
        BaseTranscriptEntity transcript = new BaseTranscriptEntity(
                videoUrl,
                "Another test transcript",
                null, // structuredContent
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
    void shouldReturnEmptyWhenVideoUrlNotFound() {
        // When
        Optional<BaseTranscriptEntity> found = baseTranscriptRepository.findByVideoUrl("https://nonexistent.com/video");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldCheckExistenceByVideoUrl() {
        // Given
        String videoUrl = "https://example.com/video3";
        BaseTranscriptEntity transcript = new BaseTranscriptEntity(
                videoUrl,
                "Existence test transcript",
                null, // structuredContent
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

    @Test
    void shouldEnforceUniqueVideoUrl() {
        // Given
        String videoUrl = "https://example.com/duplicate-video";
        BaseTranscriptEntity transcript1 = new BaseTranscriptEntity(
                videoUrl,
                "First transcript",
                null, // structuredContent
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
                null, // structuredContent
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
        try {
            baseTranscriptRepository.save(transcript2);
            baseTranscriptRepository.flush(); // Force the constraint check
            assertThat(false).as("Should have thrown constraint violation").isTrue();
        } catch (Exception e) {
            // Expected - unique constraint violation
            assertThat(e.getMessage()).containsIgnoringCase("unique");
        }
    }

    @Test
    void shouldFindByPlatformAndPlatformVideoId_whenBothSet() {
        // Given
        BaseTranscriptEntity transcript = new BaseTranscriptEntity(
                "https://www.youtube.com/watch?v=canonical1",
                "Canonical transcript",
                null,
                "desc",
                "title",
                60.0,
                Instant.now(),
                "acc", "accName", "id", "idName"
        );
        transcript.setPlatform("YOUTUBE");
        transcript.setPlatformVideoId("canonical1");
        baseTranscriptRepository.save(transcript);

        // When
        Optional<BaseTranscriptEntity> found =
                baseTranscriptRepository.findByPlatformAndPlatformVideoId("YOUTUBE", "canonical1");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getVideoUrl()).isEqualTo("https://www.youtube.com/watch?v=canonical1");
        assertThat(found.get().getPlatformVideoId()).isEqualTo("canonical1");
        assertThat(found.get().getPlatform()).isEqualTo("YOUTUBE");
    }

    @Test
    void shouldReturnEmptyForFindByPlatformAndPlatformVideoId_whenMismatch() {
        // Given
        BaseTranscriptEntity transcript = new BaseTranscriptEntity(
                "https://www.youtube.com/watch?v=mismatch",
                "Mismatch transcript",
                null, "desc", "title", 60.0, Instant.now(),
                "acc", "accName", "id", "idName"
        );
        transcript.setPlatform("YOUTUBE");
        transcript.setPlatformVideoId("vid_a");
        baseTranscriptRepository.save(transcript);

        // When/Then — different id
        assertThat(baseTranscriptRepository.findByPlatformAndPlatformVideoId("YOUTUBE", "vid_b")).isEmpty();
        // When/Then — different platform
        assertThat(baseTranscriptRepository.findByPlatformAndPlatformVideoId("TIKTOK", "vid_a")).isEmpty();
    }

    @Test
    void shouldAllowMultipleRowsWithNullPlatformVideoId() {
        // Existing legacy rows leave platform_video_id NULL. The partial unique
        // index in V23 only enforces uniqueness when both columns are non-null,
        // so legacy rows must be free to coexist. Repository-level test only
        // checks that NULLs round-trip cleanly; the partial-index behaviour
        // itself is exercised by the migration in production / integration env.
        BaseTranscriptEntity a = new BaseTranscriptEntity(
                "https://example.com/legacy-a",
                "legacy a",
                null, "desc", "title", 60.0, Instant.now(),
                "acc", "accName", "id", "idName"
        );
        a.setPlatform("YOUTUBE"); // platform set but platform_video_id NULL
        baseTranscriptRepository.save(a);

        BaseTranscriptEntity b = new BaseTranscriptEntity(
                "https://example.com/legacy-b",
                "legacy b",
                null, "desc", "title", 60.0, Instant.now(),
                "acc", "accName", "id", "idName"
        );
        b.setPlatform("YOUTUBE");
        baseTranscriptRepository.save(b);

        baseTranscriptRepository.flush(); // should NOT throw
        assertThat(baseTranscriptRepository.existsByVideoUrl("https://example.com/legacy-a")).isTrue();
        assertThat(baseTranscriptRepository.existsByVideoUrl("https://example.com/legacy-b")).isTrue();
    }

    @Test
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