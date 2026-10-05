package com.app.categorise.spike;

import com.app.categorise.domain.service.TranscriptService;
import com.app.categorise.domain.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike only (Stage 5 blast radius): do the existing deletion paths keep working, unchanged,
 * once collections and collection_items cascade from users and user_transcripts?
 * Runs every real Flyway migration plus the candidate V28 on Postgres 15 with pgvector.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CollectionsCascadeSpikeIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TranscriptService transcriptService;
    @Autowired UserService userService;

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", id, id + "@example.com");
        return id;
    }

    private UUID saved(UUID user) {
        UUID base = UUID.randomUUID(), ut = UUID.randomUUID();
        jdbc.update("INSERT INTO base_transcripts (id, video_url, transcript) VALUES (?, ?, 'text')", base, "https://e.com/" + base);
        jdbc.update("INSERT INTO user_transcripts (id, user_id, base_transcript_id) VALUES (?, ?, ?)", ut, user, base);
        return ut;
    }

    private UUID collection(UUID user, String name, List<UUID> items) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO collections (id, user_id, name, name_key) VALUES (?, ?, ?, ?)", id, user, name, name.toLowerCase());
        long key = 0;
        for (UUID ut : items) {
            jdbc.update("INSERT INTO collection_items (collection_id, user_transcript_id, sort_key) VALUES (?, ?, ?)", id, ut, key += 4294967296L);
        }
        return id;
    }

    private int items(String where, Object... args) {
        return jdbc.queryForObject("SELECT count(*) FROM collection_items WHERE " + where, Integer.class, args);
    }

    @Test
    void bulkTranscriptDeleteRemovesMembershipsThroughTheRealService() {
        UUID u = user();
        UUID a = saved(u), b = saved(u), c = saved(u);
        UUID c1 = collection(u, "One", List.of(a, b, c));
        UUID c2 = collection(u, "Two", List.of(a, c));

        transcriptService.deleteTranscripts(u, List.of(a, b));

        assertThat(items("collection_id = ?", c1)).isEqualTo(1);
        assertThat(items("collection_id = ?", c2)).isEqualTo(1);
        assertThat(items("user_transcript_id IN (?, ?)", a, b)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM collections WHERE user_id = ?", Integer.class, u)).isEqualTo(2);
    }

    @Test
    void accountDeleteSucceedsAndRemovesCollectionsThroughTheRealService() {
        UUID u = user(), other = user();
        UUID a = saved(u), b = saved(u), o = saved(other);
        collection(u, "Mine", List.of(a, b));
        UUID theirs = collection(other, "Theirs", List.of(o));

        userService.deleteAccount(u);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM collections WHERE user_id = ?", Integer.class, u)).isZero();
        assertThat(items("user_transcript_id IN (?, ?)", a, b)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, u)).isZero();
        assertThat(items("collection_id = ?", theirs)).isEqualTo(1);
    }
}
