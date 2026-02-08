package com.app.categorise.data.repository;

import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserTranscriptRepositoryTest {

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
    private UserTranscriptRepository userTranscriptRepository;

    @Autowired
    private BaseTranscriptRepository baseTranscriptRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserEntity user1;
    private UserEntity user2;
    private BaseTranscriptEntity baseTranscript1;
    private BaseTranscriptEntity baseTranscript2;
    private CategoryEntity category1;
    private CategoryEntity category2;

    @BeforeEach
    void setUp() {
        // Create users
        user1 = new UserEntity();
        user1.setSub("user1-sub");
        user1.setEmail("user1@example.com");
        user1.setName("User One");
        user1.setFirstName("User");
        user1.setLastName("One");
        user1 = entityManager.persistAndFlush(user1);

        user2 = new UserEntity();
        user2.setSub("user2-sub");
        user2.setEmail("user2@example.com");
        user2.setName("User Two");
        user2.setFirstName("User");
        user2.setLastName("Two");
        user2 = entityManager.persistAndFlush(user2);

        // Create base transcripts
        baseTranscript1 = new BaseTranscriptEntity(
                "https://example.com/video1",
                "First transcript content",
                null, // structuredContent
                "First description",
                "First Video",
                120.0,
                Instant.now().minusSeconds(3600),
                "account1",
                "account_name1",
                "identifier1",
                "identifier_name1"
        );
        baseTranscript1 = baseTranscriptRepository.save(baseTranscript1);

        baseTranscript2 = new BaseTranscriptEntity(
                "https://example.com/video2",
                "Second transcript content",
                null, // structuredContent
                "Second description",
                "Second Video",
                180.0,
                Instant.now().minusSeconds(1800),
                "account2",
                "account_name2",
                "identifier2",
                "identifier_name2"
        );
        baseTranscript2 = baseTranscriptRepository.save(baseTranscript2);

        // Create categories
        category1 = new CategoryEntity();
        category1.setName("Technology");
        category1.setDescription("Tech videos");
        category1 = entityManager.persistAndFlush(category1);

        category2 = new CategoryEntity();
        category2.setName("Education");
        category2.setDescription("Educational content");
        category2 = entityManager.persistAndFlush(category2);
    }

    @Test
    void shouldSaveAndFindUserTranscript() {
        // Given
        UserTranscriptEntity userTranscript = new UserTranscriptEntity(
            user1.getId(),
            baseTranscript1,
            category1
        );

        // When
        UserTranscriptEntity saved = userTranscriptRepository.save(userTranscript);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(user1.getId());
        assertThat(saved.getBaseTranscriptId()).isEqualTo(baseTranscript1.getId());
        assertThat(saved.getCategoryId()).isEqualTo(category1.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getLastAccessedAt()).isNotNull();
    }

    @Test
    void shouldFindByUserIdWithBaseTranscript() {
        // Given
        UserTranscriptEntity userTranscript1 = new UserTranscriptEntity(user1.getId(), baseTranscript1, category1);
        UserTranscriptEntity userTranscript2 = new UserTranscriptEntity(user1.getId(), baseTranscript2, category2);
        UserTranscriptEntity userTranscript3 = new UserTranscriptEntity(user2.getId(), baseTranscript1, null);

        userTranscriptRepository.saveAll(List.of(userTranscript1, userTranscript2, userTranscript3));
        entityManager.flush();
        entityManager.clear(); // Clear persistence context to test lazy loading

        // When
        List<UserTranscriptEntity> userTranscripts = userTranscriptRepository.findByUserIdWithBaseTranscript(user1.getId());

        // Then
        assertThat(userTranscripts).hasSize(2);
        
        // Verify JOIN FETCH worked - accessing baseTranscript shouldn't trigger additional queries
        UserTranscriptEntity first = userTranscripts.get(0);
        assertThat(first.getBaseTranscript()).isNotNull(); // Should be loaded due to JOIN FETCH
        assertThat(first.getBaseTranscript().getVideoUrl()).isIn(
                "https://example.com/video1", 
                "https://example.com/video2"
        );
        
        // Verify ordering (should be by createdAt DESC)
        assertThat(userTranscripts.get(0).getCreatedAt())
                .isAfterOrEqualTo(userTranscripts.get(1).getCreatedAt());
    }

    @Test
    void shouldFindByUserIdWithBaseTranscriptAndCategory() {
        // Given
        UserTranscriptEntity userTranscript1 = new UserTranscriptEntity(user1.getId(), baseTranscript1, category1);
        UserTranscriptEntity userTranscript2 = new UserTranscriptEntity(user1.getId(), baseTranscript2, null); // No category

        userTranscriptRepository.saveAll(List.of(userTranscript1, userTranscript2));
        entityManager.flush();
        entityManager.clear();

        // When
        List<UserTranscriptEntity> userTranscripts = userTranscriptRepository.findByUserIdWithBaseTranscriptAndCategory(user1.getId());

        // Then
        assertThat(userTranscripts).hasSize(2);
        
        // Find the one with category
        UserTranscriptEntity withCategory = userTranscripts.stream()
                .filter(ut -> ut.getCategoryId() != null)
                .findFirst()
                .orElseThrow();
        
        // Find the one without category
        UserTranscriptEntity withoutCategory = userTranscripts.stream()
                .filter(ut -> ut.getCategoryId() == null)
                .findFirst()
                .orElseThrow();

        // Verify JOIN FETCH worked for both base transcript and category
        assertThat(withCategory.getBaseTranscript()).isNotNull();
        assertThat(withCategory.getCategory()).isNotNull();
        assertThat(withCategory.getCategory().getName()).isEqualTo("Technology");

        assertThat(withoutCategory.getBaseTranscript()).isNotNull();
        assertThat(withoutCategory.getCategory()).isNull(); // LEFT JOIN should handle null category
    }

    @Test
    void shouldFindByUserIdAndBaseTranscriptIdWithBaseTranscript() {
        // Given
        UserTranscriptEntity userTranscript = new UserTranscriptEntity(user1.getId(), baseTranscript1, category1);
        userTranscriptRepository.save(userTranscript);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<UserTranscriptEntity> found = userTranscriptRepository
                .findByUserIdAndBaseTranscriptIdWithBaseTranscript(user1.getId(), baseTranscript1.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(user1.getId());
        assertThat(found.get().getBaseTranscriptId()).isEqualTo(baseTranscript1.getId());
        assertThat(found.get().getBaseTranscript()).isNotNull(); // Should be loaded due to JOIN FETCH
        assertThat(found.get().getBaseTranscript().getVideoUrl()).isEqualTo("https://example.com/video1");
    }

    @Test
    void shouldFindByUserIdAndCategoryIdWithBaseTranscript() {
        // Given
        UserTranscriptEntity userTranscript1 = new UserTranscriptEntity(user1.getId(), baseTranscript1, category1);
        UserTranscriptEntity userTranscript2 = new UserTranscriptEntity(user1.getId(), baseTranscript2, category1);
        UserTranscriptEntity userTranscript3 = new UserTranscriptEntity(user2.getId(), baseTranscript1, category2); // Use user2 to avoid duplicate

        userTranscriptRepository.saveAll(List.of(userTranscript1, userTranscript2, userTranscript3));
        entityManager.flush();
        entityManager.clear();

        // When
        List<UserTranscriptEntity> category1Transcripts = userTranscriptRepository
                .findByUserIdAndCategoryIdWithBaseTranscript(user1.getId(), category1.getId());

        // Then
        assertThat(category1Transcripts).hasSize(2);
        category1Transcripts.forEach(ut -> {
            assertThat(ut.getCategoryId()).isEqualTo(category1.getId());
            assertThat(ut.getBaseTranscript()).isNotNull(); // Should be loaded due to JOIN FETCH
        });
    }

    @Test
    void shouldCheckExistenceByUserIdAndBaseTranscriptId() {
        // Given
        UserTranscriptEntity userTranscript = new UserTranscriptEntity(user1.getId(), baseTranscript1, category1);
        userTranscriptRepository.save(userTranscript);

        // When & Then
        assertThat(userTranscriptRepository.existsByUserIdAndBaseTranscriptId(user1.getId(), baseTranscript1.getId())).isTrue();
        assertThat(userTranscriptRepository.existsByUserIdAndBaseTranscriptId(user1.getId(), baseTranscript2.getId())).isFalse();
        assertThat(userTranscriptRepository.existsByUserIdAndBaseTranscriptId(user2.getId(), baseTranscript1.getId())).isFalse();
    }

    @Test
    void shouldFindByUserIdAndVideoUrl() {
        // Given
        UserTranscriptEntity userTranscript = new UserTranscriptEntity(user1.getId(), baseTranscript1, category1);
        userTranscriptRepository.save(userTranscript);

        // When
        Optional<UserTranscriptEntity> found = userTranscriptRepository
                .findByUserIdAndVideoUrl(user1.getId(), "https://example.com/video1");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(user1.getId());
        assertThat(found.get().getBaseTranscriptId()).isEqualTo(baseTranscript1.getId());
    }

    @Test
    void shouldFindByUserIdAndVideoUrlWithFullData() {
        // Given
        UserTranscriptEntity userTranscript = new UserTranscriptEntity(user1.getId(), baseTranscript1, category1);
        userTranscriptRepository.save(userTranscript);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<UserTranscriptEntity> found = userTranscriptRepository
                .findByUserIdAndVideoUrlWithFullData(user1.getId(), "https://example.com/video1");

        // Then
        assertThat(found).isPresent();
        UserTranscriptEntity result = found.get();
        
        // Verify all data is loaded due to JOIN FETCH
        assertThat(result.getBaseTranscript()).isNotNull();
        assertThat(result.getBaseTranscript().getVideoUrl()).isEqualTo("https://example.com/video1");
        assertThat(result.getCategory()).isNotNull();
        assertThat(result.getCategory().getName()).isEqualTo("Technology");
    }

    @Test
    void shouldEnforceUniqueUserTranscriptConstraint() {
        // Given
        UserTranscriptEntity userTranscript1 = new UserTranscriptEntity(user1.getId(), baseTranscript1, category1);
        userTranscriptRepository.save(userTranscript1);

        UserTranscriptEntity userTranscript2 = new UserTranscriptEntity(user1.getId(), baseTranscript1, category2);

        // When & Then - should fail due to unique constraint on (user_id, base_transcript_id)
        try {
            userTranscriptRepository.save(userTranscript2);
            userTranscriptRepository.flush();
            assertThat(false).as("Should have thrown constraint violation").isTrue();
        } catch (Exception e) {
            // Expected - unique constraint violation
            assertThat(e.getMessage()).containsIgnoringCase("unique");
        }
    }

    @Test
    void shouldUpdateLastAccessedAtOnUpdate() throws InterruptedException {
        // Given
        UserTranscriptEntity userTranscript = new UserTranscriptEntity(user1.getId(), baseTranscript1, category1);
        UserTranscriptEntity saved = userTranscriptRepository.save(userTranscript);
        Instant originalLastAccessed = saved.getLastAccessedAt();

        // Wait a bit to ensure timestamp difference
        Thread.sleep(100); // Increased to 100ms for more reliable timestamp difference

        // When
        saved.setCategory(category2); // Trigger an update
        UserTranscriptEntity updated = userTranscriptRepository.save(saved);
        entityManager.flush(); // Ensure the @PreUpdate is triggered

        // Then
        // The @PreUpdate method should update lastAccessedAt to current time
        assertThat(updated.getLastAccessedAt()).isAfterOrEqualTo(originalLastAccessed);
    }
}