# Collections / Playlists Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add user-owned, named **Collections** that group saved transcripts in a many-to-many relationship, with manual ordering, on both the content-categorise backend and the Scoop iOS app.

**Architecture:** Mirror the existing `UserSubcategory` vertical slice on the backend — `CollectionEntity` + explicit join `CollectionTranscriptEntity` (composite `@EmbeddedId`), `JpaRepository` repos, a `@Service @Transactional` `CollectionService`, a `@Component CollectionMapper`, record DTOs, and a `@RestController @RequestMapping("/api/v1")` `CollectionController` using `@AuthenticationPrincipal UserPrincipal` + `requireUser(...)`. Membership is a join property surfaced collection-side via a transient `contains` flag (never on the transcript DTO). iOS mirrors the `CategoryService`/`Subcategory` slice with a `Codable CollectionResponse`, a `CollectionService` over `HTTPClient`, and SwiftUI screens reusing `TranscriptCard`.

**Tech Stack:** Backend — Java 24, Spring Boot 3.5, Maven, PostgreSQL + Flyway (migration **V28**, latest existing is V27), JUnit 5 + Mockito + AssertJ + Testcontainers. iOS — SwiftUI app "Scoop", target iOS 18.x, Swift Testing (`@Test`/`#expect`) with `MockURLProtocol`.

---

## Conventions (read before starting)

- **Base package:** `com.app.categorise`.
- **Test grouping (backend AGENTS.md):** Group tests in a file by the method under test using `@Nested` classes — **exactly one nesting layer**, no deeper.
- **iOS files are auto-included:** the Xcode project uses `PBXFileSystemSynchronizedRootGroup`, so new `.swift` files dropped into the correct folder are compiled without editing `project.pbxproj`.
- **iOS test command (used in every iOS step):**
  ```bash
  xcodebuild test \
    -project Scoop.xcodeproj \
    -scheme Scoop-Local \
    -destination 'platform=iOS Simulator,name=iPhone 16' \
    -only-testing:ScoopTests/<SuiteName>/<testName>
  ```
- **Backend test command shape:** `./mvnw test -Dtest=<ClassName>#<methodName>` (single method) or `-Dtest=<ClassName>` (whole class).
- **Commit after every green test.** Conventional Commit messages (`feat:`, `test:`, `chore:`).

---

## File Structure

### Backend — create
- `src/main/resources/db/migration/V28__add_collections.sql` — schema for `collection` + `collection_transcript`.
- `data/entity/CollectionEntity.java` — JPA entity for `collection`.
- `data/entity/CollectionTranscriptId.java` — `@Embeddable` composite key class.
- `data/entity/CollectionTranscriptEntity.java` — explicit join entity (`@EmbeddedId`).
- `data/repository/CollectionRepository.java` — ownership-scoped finders.
- `data/repository/CollectionTranscriptRepository.java` — membership/count/order queries.
- `data/repository/CollectionCountProjection.java` — batch count projection.
- `domain/model/Collection.java` — pure domain model.
- `domain/service/CollectionService.java` — CRUD + membership + reorder + limits.
- `application/mapper/CollectionMapper.java` — entity → `CollectionDto`.
- `api/dto/CollectionDto.java` — record (nullable `contains`).
- `api/dto/CreateCollectionRequest.java` — record (`@NotBlank` name).
- `api/dto/UpdateCollectionRequest.java` — record (nullable fields).
- `api/dto/ReorderCollectionRequest.java` — record (`userTranscriptIds`).
- `api/controller/CollectionController.java` — REST endpoints.
- `exception/CollectionNotFoundException.java` — 404.
- `exception/CollectionLimitExceededException.java` — 422.

### Backend — modify
- `exception/GlobalExceptionHandler.java` — map the two new exceptions.

### Backend — test
- `src/test/java/com/app/categorise/data/repository/CollectionTranscriptRepositoryTest.java`
- `src/test/java/com/app/categorise/domain/service/CollectionServiceTest.java`
- `src/test/java/com/app/categorise/api/controller/CollectionControllerTest.java`

### iOS (Scoop) — create
- `Scoop/models/api/CollectionResponse.swift` — `Codable` mirroring `CollectionDto`.
- `Scoop/models/domain/Collection.swift` — `struct Collection: Identifiable`.
- `Scoop/models/mapper/CollectionMapper.swift` — `CollectionResponse → Collection`.
- `Scoop/service/CollectionService.swift` — static methods over `HTTPClient`.
- `Scoop/views/Feed/CollectionPickerSheet.swift` — multi-select picker using `contains`.
- `Scoop/views/Collections/CollectionsScreen.swift` — list of collections.
- `Scoop/viewModel/CollectionsViewModel.swift` — view model for the list (view models live in `Scoop/viewModel/`, matching `FeedViewModel`/`DashboardViewModel`).
- `Scoop/views/Collections/CollectionDetailScreen.swift` — members + drag-to-reorder.
- `Scoop/viewModel/CollectionDetailViewModel.swift` — paging + reorder.

### iOS (Scoop) — modify
- `Scoop/views/Dashboard/DashboardScreen.swift` — add a "Collections" entry.

### iOS (Scoop) — test
- `ScoopTests/CollectionServiceTests.swift` — decode + add/remove/reorder/contains/conflict.

---

## Chunk 1: Database migration & JPA entities

### Task 1: Flyway migration V28

**Files:**
- Create: `src/main/resources/db/migration/V28__add_collections.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Collections: user-owned named groupings of transcripts
CREATE TABLE collection (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_collection_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_collection_user_id ON collection(user_id);

-- Many-to-many join: which user_transcripts belong to which collection
CREATE TABLE collection_transcript (
    collection_id UUID NOT NULL
        REFERENCES collection(id) ON DELETE CASCADE,
    user_transcript_id UUID NOT NULL
        REFERENCES user_transcripts(id) ON DELETE CASCADE,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    position INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_collection_transcript
        PRIMARY KEY (collection_id, user_transcript_id)
);

CREATE INDEX idx_collection_transcript_collection_id
    ON collection_transcript(collection_id);
CREATE INDEX idx_collection_transcript_user_transcript_id
    ON collection_transcript(user_transcript_id);
CREATE INDEX idx_collection_transcript_collection_position
    ON collection_transcript(collection_id, position);
```

- [ ] **Step 2: Verify the migration is the next version and parses**

Run: `ls src/main/resources/db/migration/ | sort -V | tail -3`
Expected: `V27__unique_app_store_original_transaction.sql` then `V28__add_collections.sql` — V28 is the new latest, no gap/duplicate.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V28__add_collections.sql
git commit -m "feat: add V28 collections migration"
```

> The repository test in Task 5 is the real validation that this migration applies cleanly (Flyway runs against Testcontainers). No standalone implementation step is needed here.

---

### Task 2: `CollectionEntity`

**Files:**
- Create: `src/main/java/com/app/categorise/data/entity/CollectionEntity.java`

This entity has no isolated unit test; it is exercised by the repository test (Task 5) and service tests (Chunk 3). Write it now so those compile.

- [ ] **Step 1: Write the entity** (mirrors `UserSubcategoryEntity`, adds `updated_at` + `@PreUpdate`)

```java
package com.app.categorise.data.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collection", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "name"})
})
public class CollectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CollectionEntity() {}

    public CollectionEntity(UUID userId, String name, String description) {
        this.userId = userId;
        this.name = name;
        this.description = description;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -q -o compile`
Expected: BUILD SUCCESS (no errors referencing `CollectionEntity`).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/app/categorise/data/entity/CollectionEntity.java
git commit -m "feat: add CollectionEntity"
```

---

### Task 3: `CollectionTranscriptId` + `CollectionTranscriptEntity` (explicit join)

**Files:**
- Create: `src/main/java/com/app/categorise/data/entity/CollectionTranscriptId.java`
- Create: `src/main/java/com/app/categorise/data/entity/CollectionTranscriptEntity.java`

Use `@EmbeddedId` (per the decided requirements). The embeddable holds the two FK UUIDs; the entity maps the same columns read-only via `@MapsId` on `@ManyToOne` associations.

- [ ] **Step 1: Write the composite key class**

```java
package com.app.categorise.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CollectionTranscriptId implements Serializable {

    @Column(name = "collection_id")
    private UUID collectionId;

    @Column(name = "user_transcript_id")
    private UUID userTranscriptId;

    public CollectionTranscriptId() {}

    public CollectionTranscriptId(UUID collectionId, UUID userTranscriptId) {
        this.collectionId = collectionId;
        this.userTranscriptId = userTranscriptId;
    }

    public UUID getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(UUID collectionId) {
        this.collectionId = collectionId;
    }

    public UUID getUserTranscriptId() {
        return userTranscriptId;
    }

    public void setUserTranscriptId(UUID userTranscriptId) {
        this.userTranscriptId = userTranscriptId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CollectionTranscriptId that)) {
            return false;
        }
        return Objects.equals(collectionId, that.collectionId)
            && Objects.equals(userTranscriptId, that.userTranscriptId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionId, userTranscriptId);
    }
}
```

- [ ] **Step 2: Write the join entity**

```java
package com.app.categorise.data.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "collection_transcript")
public class CollectionTranscriptEntity {

    @EmbeddedId
    private CollectionTranscriptId id;

    @MapsId("collectionId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private CollectionEntity collection;

    @MapsId("userTranscriptId")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_transcript_id", nullable = false)
    private UserTranscriptEntity userTranscript;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "position", nullable = false)
    private int position;

    public CollectionTranscriptEntity() {}

    public CollectionTranscriptEntity(CollectionEntity collection, UserTranscriptEntity userTranscript, int position) {
        this.collection = collection;
        this.userTranscript = userTranscript;
        this.position = position;
        this.id = new CollectionTranscriptId(collection.getId(), userTranscript.getId());
    }

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }

    public CollectionTranscriptId getId() {
        return id;
    }

    public void setId(CollectionTranscriptId id) {
        this.id = id;
    }

    public CollectionEntity getCollection() {
        return collection;
    }

    public void setCollection(CollectionEntity collection) {
        this.collection = collection;
    }

    public UserTranscriptEntity getUserTranscript() {
        return userTranscript;
    }

    public void setUserTranscript(UserTranscriptEntity userTranscript) {
        this.userTranscript = userTranscript;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/app/categorise/data/entity/CollectionTranscriptId.java src/main/java/com/app/categorise/data/entity/CollectionTranscriptEntity.java
git commit -m "feat: add CollectionTranscriptEntity join with composite key"
```

---

## Chunk 2: Repositories & repository (Testcontainers) test

### Task 4: Repositories + count projection

**Files:**
- Create: `src/main/java/com/app/categorise/data/repository/CollectionCountProjection.java`
- Create: `src/main/java/com/app/categorise/data/repository/CollectionRepository.java`
- Create: `src/main/java/com/app/categorise/data/repository/CollectionTranscriptRepository.java`

These interfaces are validated by the repository test in Task 5; write them first so the test compiles.

- [ ] **Step 1: Write the count projection**

```java
package com.app.categorise.data.repository;

import java.util.UUID;

/**
 * Projection for batch member counts per collection (avoids N+1 on list view).
 */
public interface CollectionCountProjection {
    UUID getId();
    long getCnt();
}
```

- [ ] **Step 2: Write `CollectionRepository`** (mirrors `UserSubcategoryRepository`)

```java
package com.app.categorise.data.repository;

import com.app.categorise.data.entity.CollectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionRepository extends JpaRepository<CollectionEntity, UUID> {

    List<CollectionEntity> findByUserId(UUID userId);

    Optional<CollectionEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<CollectionEntity> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    long countByUserId(UUID userId);

    @Modifying
    @Transactional
    void deleteByUserId(UUID userId);
}
```

- [ ] **Step 3: Write `CollectionTranscriptRepository`**

```java
package com.app.categorise.data.repository;

import com.app.categorise.data.entity.CollectionTranscriptEntity;
import com.app.categorise.data.entity.CollectionTranscriptId;
import com.app.categorise.data.entity.UserTranscriptEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionTranscriptRepository
        extends JpaRepository<CollectionTranscriptEntity, CollectionTranscriptId> {

    boolean existsByCollection_IdAndUserTranscript_Id(UUID collectionId, UUID userTranscriptId);

    long countByCollection_Id(UUID collectionId);

    Optional<CollectionTranscriptEntity> findByCollection_IdAndUserTranscript_Id(UUID collectionId, UUID userTranscriptId);

    @Modifying
    @Transactional
    void deleteByCollection_IdAndUserTranscript_Id(UUID collectionId, UUID userTranscriptId);

    /** Highest current position in a collection, or null when empty. */
    @Query("SELECT MAX(ct.position) FROM CollectionTranscriptEntity ct WHERE ct.collection.id = :collectionId")
    Integer findMaxPosition(@Param("collectionId") UUID collectionId);

    /** All join rows for the given supplied transcript ids within one collection (for reorder validation). */
    List<CollectionTranscriptEntity> findByCollection_IdAndUserTranscript_IdIn(UUID collectionId, List<UUID> userTranscriptIds);

    /** Batch member counts for the user's collections (avoids N+1 on list view). */
    @Query("SELECT ct.collection.id AS id, COUNT(ct) AS cnt FROM CollectionTranscriptEntity ct " +
           "WHERE ct.collection.userId = :userId GROUP BY ct.collection.id")
    List<CollectionCountProjection> countsByUser(@Param("userId") UUID userId);

    /** Join rows for one transcript across the user's collections (drives the `contains` flag). */
    @Query("SELECT ct.collection.id FROM CollectionTranscriptEntity ct " +
           "WHERE ct.collection.userId = :userId AND ct.userTranscript.id = :userTranscriptId")
    List<UUID> findCollectionIdsContaining(@Param("userId") UUID userId,
                                           @Param("userTranscriptId") UUID userTranscriptId);

    /** Ordered member page: position ASC, then added_at ASC as a stable tiebreak. */
    @Query("SELECT ct.userTranscript FROM CollectionTranscriptEntity ct " +
           "WHERE ct.collection.id = :collectionId " +
           "ORDER BY ct.position ASC, ct.addedAt ASC")
    Page<UserTranscriptEntity> findMembers(@Param("collectionId") UUID collectionId, Pageable pageable);
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/data/repository/CollectionCountProjection.java src/main/java/com/app/categorise/data/repository/CollectionRepository.java src/main/java/com/app/categorise/data/repository/CollectionTranscriptRepository.java
git commit -m "feat: add Collection repositories"
```

---

### Task 5: `CollectionTranscriptRepositoryTest` (Testcontainers)

**Files:**
- Test: `src/test/java/com/app/categorise/data/repository/CollectionTranscriptRepositoryTest.java`

Validates: composite-PK uniqueness, `countByCollection_Id`, ordered `findMembers`, and cascade behaviour (delete collection → join rows gone, transcript kept; delete transcript → join rows gone, collection kept). Tests are grouped one `@Nested` layer deep by method/behaviour under test.

- [ ] **Step 1: Write the failing test**

```java
package com.app.categorise.data.repository;

import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.CollectionEntity;
import com.app.categorise.data.entity.CollectionTranscriptEntity;
import com.app.categorise.data.entity.CollectionTranscriptId;
import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CollectionTranscriptRepositoryTest {

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
    private CollectionRepository collectionRepository;

    @Autowired
    private CollectionTranscriptRepository joinRepository;

    @Autowired
    private BaseTranscriptRepository baseTranscriptRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserEntity user;
    private CollectionEntity collection;
    private UserTranscriptEntity transcriptA;
    private UserTranscriptEntity transcriptB;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setSub("user-sub");
        user.setEmail("user@example.com");
        user.setName("User One");
        user.setFirstName("User");
        user.setLastName("One");
        user = entityManager.persistAndFlush(user);

        BaseTranscriptEntity baseA = baseTranscriptRepository.save(new BaseTranscriptEntity(
                "https://example.com/a", "content a", null, "desc a", "Video A", 120.0,
                Instant.now().minusSeconds(3600), "acc", "acc name", "id", "id name"));
        BaseTranscriptEntity baseB = baseTranscriptRepository.save(new BaseTranscriptEntity(
                "https://example.com/b", "content b", null, "desc b", "Video B", 120.0,
                Instant.now().minusSeconds(1800), "acc", "acc name", "id2", "id name2"));

        transcriptA = entityManager.persistAndFlush(new UserTranscriptEntity(user.getId(), baseA, null));
        transcriptB = entityManager.persistAndFlush(new UserTranscriptEntity(user.getId(), baseB, null));

        collection = collectionRepository.save(new CollectionEntity(user.getId(), "Weeknight dinners", null));
    }

    @Nested
    @DisplayName("save (composite PK)")
    class Save {

        @Test
        @DisplayName("rejects a duplicate (collection, transcript) pair")
        void rejectsDuplicateMembership() {
            joinRepository.saveAndFlush(new CollectionTranscriptEntity(collection, transcriptA, 0));

            assertThatThrownBy(() ->
                joinRepository.saveAndFlush(new CollectionTranscriptEntity(collection, transcriptA, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("countByCollection_Id")
    class CountByCollection {

        @Test
        @DisplayName("counts only members of the given collection")
        void countsMembers() {
            joinRepository.saveAndFlush(new CollectionTranscriptEntity(collection, transcriptA, 0));
            joinRepository.saveAndFlush(new CollectionTranscriptEntity(collection, transcriptB, 1));

            assertThat(joinRepository.countByCollection_Id(collection.getId())).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findMembers")
    class FindMembers {

        @Test
        @DisplayName("returns members ordered by position ASC")
        void ordersByPosition() {
            joinRepository.saveAndFlush(new CollectionTranscriptEntity(collection, transcriptB, 0));
            joinRepository.saveAndFlush(new CollectionTranscriptEntity(collection, transcriptA, 1));

            List<UserTranscriptEntity> members =
                joinRepository.findMembers(collection.getId(), PageRequest.of(0, 10)).getContent();

            assertThat(members).extracting(UserTranscriptEntity::getId)
                .containsExactly(transcriptB.getId(), transcriptA.getId());
        }
    }

    @Nested
    @DisplayName("cascade on delete")
    class Cascade {

        @Test
        @DisplayName("deleting a collection removes its join rows but keeps the transcript")
        void deletingCollectionCascadesJoinRows() {
            joinRepository.saveAndFlush(new CollectionTranscriptEntity(collection, transcriptA, 0));

            collectionRepository.delete(collection);
            entityManager.flush();
            entityManager.clear();

            assertThat(joinRepository.existsByCollection_IdAndUserTranscript_Id(collection.getId(), transcriptA.getId()))
                .isFalse();
            assertThat(entityManager.find(UserTranscriptEntity.class, transcriptA.getId())).isNotNull();
        }

        @Test
        @DisplayName("deleting a user_transcript removes its join rows but keeps the collection")
        void deletingTranscriptCascadesJoinRows() {
            joinRepository.saveAndFlush(new CollectionTranscriptEntity(collection, transcriptA, 0));

            entityManager.remove(entityManager.find(UserTranscriptEntity.class, transcriptA.getId()));
            entityManager.flush();
            entityManager.clear();

            assertThat(joinRepository.existsByCollection_IdAndUserTranscript_Id(collection.getId(), transcriptA.getId()))
                .isFalse();
            assertThat(collectionRepository.findById(collection.getId())).isPresent();
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (then passes once entities/repos are correct)**

Run: `./mvnw -o test -Dtest=CollectionTranscriptRepositoryTest`
Expected first run: if any entity/repo wiring is wrong it FAILS (e.g. mapping error or cascade not applied). If green immediately, that is acceptable here because the entities/migration from Chunk 1 are the implementation — this test is the validation gate for them.

- [ ] **Step 3: Fix any mapping/migration issues until green**

If cascade assertions fail, re-check `ON DELETE CASCADE` on both FKs in `V28__add_collections.sql` (Task 1). If the duplicate test does not throw, re-check the composite `@EmbeddedId`/`pk_collection_transcript`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=CollectionTranscriptRepositoryTest`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/app/categorise/data/repository/CollectionTranscriptRepositoryTest.java
git commit -m "test: add CollectionTranscriptRepository Testcontainers test"
```

---

## Chunk 3: Domain model, exceptions, DTOs, mapper

### Task 6: `Collection` domain model

**Files:**
- Create: `src/main/java/com/app/categorise/domain/model/Collection.java`

- [ ] **Step 1: Write the domain model** (pure Java, like `UserTranscript`)

```java
package com.app.categorise.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Collection - a user-owned, named grouping of transcripts.
 * Membership is NOT held here; counts/members are returned via DTOs.
 */
public class Collection {
    private UUID id;
    private UUID userId;
    private String name;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public Collection() {}

    public Collection(UUID id, UUID userId, String name, String description,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/app/categorise/domain/model/Collection.java
git commit -m "feat: add Collection domain model"
```

---

### Task 7: Exceptions + global handler mapping

**Files:**
- Create: `src/main/java/com/app/categorise/exception/CollectionNotFoundException.java`
- Create: `src/main/java/com/app/categorise/exception/CollectionLimitExceededException.java`
- Create: `src/main/java/com/app/categorise/exception/CollectionNameConflictException.java`
- Modify: `src/main/java/com/app/categorise/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Write `CollectionNotFoundException`** (mirrors `SubcategoryNotFoundException`)

```java
package com.app.categorise.exception;

public class CollectionNotFoundException extends RuntimeException {
    public CollectionNotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Write `CollectionLimitExceededException`**

```java
package com.app.categorise.exception;

public class CollectionLimitExceededException extends RuntimeException {
    public CollectionLimitExceededException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Write `CollectionNameConflictException`** (spec §9 → 409; mirrors `SubcategoryParentMismatchException` which the handler maps to CONFLICT)

```java
package com.app.categorise.exception;

public class CollectionNameConflictException extends RuntimeException {
    public CollectionNameConflictException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Map all three in `GlobalExceptionHandler`**

Add these three handler methods immediately after the existing `handleSubcategoryNotFound` method (around line 42). Note `CollectionLimitExceededException` → **422** (mirrors `VideoProcessingException`), and `CollectionNameConflictException` → **409** (mirrors `SubcategoryParentMismatchException`):

```java
    @ExceptionHandler(CollectionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCollectionNotFound(CollectionNotFoundException ex, WebRequest request) {
        logger.warn("Collection not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(CollectionLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleCollectionLimitExceeded(CollectionLimitExceededException ex, WebRequest request) {
        logger.warn("Collection limit exceeded: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request);
    }

    @ExceptionHandler(CollectionNameConflictException.class)
    public ResponseEntity<ErrorResponse> handleCollectionNameConflict(CollectionNameConflictException ex, WebRequest request) {
        logger.warn("Collection name conflict: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex, request);
    }
```

- [ ] **Step 5: Verify it compiles**

Run: `./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/app/categorise/exception/CollectionNotFoundException.java src/main/java/com/app/categorise/exception/CollectionLimitExceededException.java src/main/java/com/app/categorise/exception/CollectionNameConflictException.java src/main/java/com/app/categorise/exception/GlobalExceptionHandler.java
git commit -m "feat: add Collection exceptions and global handler mapping"
```

---

### Task 8: DTOs

**Files:**
- Create: `src/main/java/com/app/categorise/api/dto/CollectionDto.java`
- Create: `src/main/java/com/app/categorise/api/dto/CreateCollectionRequest.java`
- Create: `src/main/java/com/app/categorise/api/dto/UpdateCollectionRequest.java`
- Create: `src/main/java/com/app/categorise/api/dto/ReorderCollectionRequest.java`

- [ ] **Step 1: Write `CollectionDto`** (`contains` non-null ONLY on the `?transcriptId=` variant)

```java
package com.app.categorise.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectionDto(
    UUID id,
    String name,
    String description,
    long transcriptCount,
    Instant createdAt,
    Instant updatedAt,
    Boolean contains
) {}
```

> `@JsonInclude(NON_NULL)` keeps `contains` out of the JSON on the plain list endpoint (where it is `null`), and emits `true`/`false` only on the `?transcriptId=` variant — matching §4.5.

- [ ] **Step 2: Write `CreateCollectionRequest`** (`@NotBlank` like `CreateSubcategoryRequest`)

```java
package com.app.categorise.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCollectionRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 4000) String description
) {}
```

- [ ] **Step 3: Write `UpdateCollectionRequest`** (nullable fields = unchanged, like `UpdateSubcategoryRequest`)

```java
package com.app.categorise.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateCollectionRequest(
    @Size(max = 255) String name,
    @Size(max = 4000) String description
) {}
```

- [ ] **Step 4: Write `ReorderCollectionRequest`**

```java
package com.app.categorise.api.dto;

import java.util.List;
import java.util.UUID;

public record ReorderCollectionRequest(
    List<UUID> userTranscriptIds
) {}
```

- [ ] **Step 5: Verify it compiles**

Run: `./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/app/categorise/api/dto/CollectionDto.java src/main/java/com/app/categorise/api/dto/CreateCollectionRequest.java src/main/java/com/app/categorise/api/dto/UpdateCollectionRequest.java src/main/java/com/app/categorise/api/dto/ReorderCollectionRequest.java
git commit -m "feat: add Collection DTOs"
```

---

### Task 9: `CollectionMapper`

**Files:**
- Create: `src/main/java/com/app/categorise/application/mapper/CollectionMapper.java`

- [ ] **Step 1: Write the mapper** (mirrors `UserSubcategoryMapper`, with count + optional `contains`)

```java
package com.app.categorise.application.mapper;

import com.app.categorise.api.dto.CollectionDto;
import com.app.categorise.data.entity.CollectionEntity;
import org.springframework.stereotype.Component;

@Component
public class CollectionMapper {

    /** List/detail variant: no membership question, so {@code contains} is null. */
    public CollectionDto toDto(CollectionEntity entity, long transcriptCount) {
        return toDto(entity, transcriptCount, null);
    }

    /** Picker variant: {@code contains} answers "does this collection contain the queried transcript?". */
    public CollectionDto toDto(CollectionEntity entity, long transcriptCount, Boolean contains) {
        return new CollectionDto(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            transcriptCount,
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            contains
        );
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/app/categorise/application/mapper/CollectionMapper.java
git commit -m "feat: add CollectionMapper"
```

---

## Chunk 4: `CollectionService`

### Task 10: `CollectionService`

**Files:**
- Create: `src/main/java/com/app/categorise/domain/service/CollectionService.java`

Mirrors `UserSubcategoryService` (constructor injection, `@Transactional` writes, `normaliseName`, `validateUserId`, `ensureNameIsAvailable`, `getOwnedCollection` via `findByIdAndUserId`). Add/remove mirror the `setSubcategory` double-ownership pattern. Soft caps from §4.7 throw `CollectionLimitExceededException`. Reorder implements the partial-reorder rules from §4.6. Members page is built exactly like `pagedFilteredTranscripts` (§8) and returns `TranscriptPageResponse`.

This service is validated by its unit tests in Task 11; write it now so the test compiles.

- [ ] **Step 1: Write the service**

```java
package com.app.categorise.domain.service;

import com.app.categorise.api.dto.TranscriptPageResponse;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.data.entity.CollectionEntity;
import com.app.categorise.data.entity.CollectionTranscriptEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.CollectionCountProjection;
import com.app.categorise.data.repository.CollectionRepository;
import com.app.categorise.data.repository.CollectionTranscriptRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.exception.CollectionLimitExceededException;
import com.app.categorise.exception.CollectionNotFoundException;
import com.app.categorise.exception.TranscriptNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CollectionService {

    /** Soft caps from §4.7. */
    static final int MAX_COLLECTIONS_PER_USER = 200;
    static final int MAX_TRANSCRIPTS_PER_COLLECTION = 5000;

    private final CollectionRepository collectionRepository;
    private final CollectionTranscriptRepository joinRepository;
    private final UserTranscriptRepository userTranscriptRepository;
    private final VideoMapper videoMapper;

    public CollectionService(CollectionRepository collectionRepository,
                             CollectionTranscriptRepository joinRepository,
                             UserTranscriptRepository userTranscriptRepository,
                             VideoMapper videoMapper) {
        this.collectionRepository = collectionRepository;
        this.joinRepository = joinRepository;
        this.userTranscriptRepository = userTranscriptRepository;
        this.videoMapper = videoMapper;
    }

    @Transactional
    public CollectionEntity createCollection(UUID userId, String name, String description) {
        validateUserId(userId);
        String normalisedName = normaliseName(name);
        if (collectionRepository.countByUserId(userId) >= MAX_COLLECTIONS_PER_USER) {
            throw new CollectionLimitExceededException(
                "Collection limit reached (" + MAX_COLLECTIONS_PER_USER + " per user)");
        }
        ensureNameIsAvailable(userId, normalisedName, null);
        return collectionRepository.save(new CollectionEntity(userId, normalisedName, description));
    }

    public List<CollectionEntity> listCollections(UUID userId) {
        validateUserId(userId);
        return collectionRepository.findByUserId(userId);
    }

    /** Member counts for the user's collections, keyed by collection id (avoids N+1). */
    public Map<UUID, Long> memberCounts(UUID userId) {
        validateUserId(userId);
        Map<UUID, Long> counts = new HashMap<>();
        for (CollectionCountProjection row : joinRepository.countsByUser(userId)) {
            counts.put(row.getId(), row.getCnt());
        }
        return counts;
    }

    /** Collection ids (among the user's collections) that contain the given transcript — drives the {@code contains} flag (§4.5). */
    public java.util.Set<UUID> collectionIdsContaining(UUID userId, UUID userTranscriptId) {
        validateUserId(userId);
        return new java.util.HashSet<>(joinRepository.findCollectionIdsContaining(userId, userTranscriptId));
    }

    @Transactional
    public CollectionEntity updateCollection(UUID userId, UUID collectionId, String name, String description) {
        CollectionEntity entity = getOwnedCollection(userId, collectionId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));
        if (name != null) {
            String normalisedName = normaliseName(name);
            ensureNameIsAvailable(userId, normalisedName, entity.getId());
            entity.setName(normalisedName);
        }
        if (description != null) {
            entity.setDescription(description);
        }
        return collectionRepository.save(entity);
    }

    @Transactional
    public void deleteCollection(UUID userId, UUID collectionId) {
        CollectionEntity entity = getOwnedCollection(userId, collectionId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));
        collectionRepository.delete(entity);
    }

    @Transactional
    public void addTranscript(UUID userId, UUID collectionId, UUID userTranscriptId) {
        CollectionEntity collection = getOwnedCollection(userId, collectionId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));
        UserTranscriptEntity transcript = userTranscriptRepository.findByIdAndUserId(userTranscriptId, userId)
            .orElseThrow(() -> new TranscriptNotFoundException("Transcript not found: " + userTranscriptId));
        if (joinRepository.existsByCollection_IdAndUserTranscript_Id(collectionId, userTranscriptId)) {
            return; // idempotent no-op — already a member (§9)
        }
        if (joinRepository.countByCollection_Id(collectionId) >= MAX_TRANSCRIPTS_PER_COLLECTION) {
            throw new CollectionLimitExceededException(
                "Collection is full (" + MAX_TRANSCRIPTS_PER_COLLECTION + " transcripts)");
        }
        Integer maxPosition = joinRepository.findMaxPosition(collectionId);
        int position = (maxPosition == null ? -1 : maxPosition) + 1; // COALESCE(MAX(position),-1)+1
        joinRepository.save(new CollectionTranscriptEntity(collection, transcript, position));
    }

    @Transactional
    public void removeTranscript(UUID userId, UUID collectionId, UUID userTranscriptId) {
        getOwnedCollection(userId, collectionId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));
        // Not-a-member is a no-op (idempotent, §9); delete-by-key is safe either way.
        joinRepository.deleteByCollection_IdAndUserTranscript_Id(collectionId, userTranscriptId);
    }

    public TranscriptPageResponse listTranscripts(UUID userId, UUID collectionId, int page, int size) {
        getOwnedCollection(userId, collectionId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));
        Page<UserTranscriptEntity> members =
            joinRepository.findMembers(collectionId, PageRequest.of(page, size));
        List<TranscriptDtoWithAliases> items = members.getContent().stream()
            .map(ut -> videoMapper.buildResponse(ut.getBaseTranscript(), ut))
            .toList();
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) members.getTotalElements() / size);
        return new TranscriptPageResponse(
            items,
            page,
            size,
            members.getTotalElements(),
            totalPages,
            page + 1 < totalPages
        );
    }

    @Transactional
    public void reorderTranscripts(UUID userId, UUID collectionId, List<UUID> userTranscriptIds) {
        getOwnedCollection(userId, collectionId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));
        if (userTranscriptIds == null || userTranscriptIds.size() <= 1) {
            return; // empty / single-element → no-op (§9)
        }
        List<CollectionTranscriptEntity> rows =
            joinRepository.findByCollection_IdAndUserTranscript_IdIn(collectionId, userTranscriptIds);
        if (rows.size() != userTranscriptIds.size()) {
            int unknown = userTranscriptIds.size() - rows.size();
            throw new IllegalArgumentException(
                "Reorder contains " + unknown + " id(s) that are not members of this collection");
        }
        Map<UUID, CollectionTranscriptEntity> byTranscriptId = new HashMap<>();
        int minPosition = Integer.MAX_VALUE;
        for (CollectionTranscriptEntity row : rows) {
            byTranscriptId.put(row.getUserTranscript().getId(), row);
            minPosition = Math.min(minPosition, row.getPosition());
        }
        int position = minPosition; // contiguous from the min current position among the supplied ids (§4.6)
        for (UUID id : userTranscriptIds) {
            byTranscriptId.get(id).setPosition(position++);
        }
        joinRepository.saveAll(rows);
    }

    public Optional<CollectionEntity> getOwnedCollection(UUID userId, UUID collectionId) {
        validateUserId(userId);
        if (collectionId == null) {
            return Optional.empty();
        }
        return collectionRepository.findByIdAndUserId(collectionId, userId);
    }

    private void ensureNameIsAvailable(UUID userId, String name, UUID currentId) {
        collectionRepository.findByUserIdAndNameIgnoreCase(userId, name)
            .filter(existing -> currentId == null || !existing.getId().equals(currentId))
            .ifPresent(existing -> {
                // spec §9: duplicate name for the same user -> 409 Conflict
                throw new CollectionNameConflictException("Collection name already exists");
            });
    }

    private String normaliseName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be blank");
        }
        return name.trim();
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/app/categorise/domain/service/CollectionService.java
git commit -m "feat: add CollectionService"
```

---

### Task 11: `CollectionServiceTest` (Mockito + AssertJ)

**Files:**
- Test: `src/test/java/com/app/categorise/domain/service/CollectionServiceTest.java`

Pure unit test with `@Mock` repositories + `VideoMapper` and `@InjectMocks CollectionService`. Per backend AGENTS.md, group with **exactly one** `@Nested` layer, keyed by the method under test. Covers: caps (422), ownership (404), idempotent add, remove no-op, reorder partial + unknown-id (400) + empty no-op, and the `contains` lookup.

- [ ] **Step 1: Write the failing test**

```java
package com.app.categorise.domain.service;

import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.data.entity.CollectionEntity;
import com.app.categorise.data.entity.CollectionTranscriptEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.CollectionRepository;
import com.app.categorise.data.repository.CollectionTranscriptRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.exception.CollectionLimitExceededException;
import com.app.categorise.exception.CollectionNotFoundException;
import com.app.categorise.exception.TranscriptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private CollectionTranscriptRepository joinRepository;
    @Mock private UserTranscriptRepository userTranscriptRepository;
    @Mock private VideoMapper videoMapper;

    @InjectMocks private CollectionService service;

    private UUID userId;
    private UUID collectionId;
    private UUID transcriptId;
    private CollectionEntity collection;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        transcriptId = UUID.randomUUID();
        collection = new CollectionEntity(userId, "Favourites", null);
        collection.setId(collectionId);
    }

    @Nested
    @DisplayName("createCollection")
    class CreateCollection {

        @Test
        @DisplayName("throws 422 limit exception when at the 200-collection cap")
        void rejectsWhenAtCap() {
            when(collectionRepository.countByUserId(userId)).thenReturn(200L);

            assertThatThrownBy(() -> service.createCollection(userId, "New", null))
                .isInstanceOf(CollectionLimitExceededException.class);
            verify(collectionRepository, never()).save(any());
        }

        @Test
        @DisplayName("trims the name and saves when under the cap")
        void savesNormalisedName() {
            when(collectionRepository.countByUserId(userId)).thenReturn(3L);
            when(collectionRepository.findByUserIdAndNameIgnoreCase(userId, "New")).thenReturn(Optional.empty());
            when(collectionRepository.save(any(CollectionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CollectionEntity saved = service.createCollection(userId, "  New  ", null);

            assertThat(saved.getName()).isEqualTo("New");
        }

        @Test
        @DisplayName("rejects a duplicate name for the same user")
        void rejectsDuplicateName() {
            when(collectionRepository.countByUserId(userId)).thenReturn(1L);
            when(collectionRepository.findByUserIdAndNameIgnoreCase(userId, "Favourites"))
                .thenReturn(Optional.of(collection));

            assertThatThrownBy(() -> service.createCollection(userId, "Favourites", null))
                .isInstanceOf(CollectionNameConflictException.class);
        }
    }

    @Nested
    @DisplayName("updateCollection")
    class UpdateCollection {

        @Test
        @DisplayName("throws 404 when the collection is not owned by the user")
        void notFoundWhenNotOwned() {
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateCollection(userId, collectionId, "X", null))
                .isInstanceOf(CollectionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("addTranscript")
    class AddTranscript {

        @Test
        @DisplayName("throws 404 when the collection is not owned")
        void notFoundCollection() {
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addTranscript(userId, collectionId, transcriptId))
                .isInstanceOf(CollectionNotFoundException.class);
        }

        @Test
        @DisplayName("throws 404 when the transcript is not owned")
        void notFoundTranscript() {
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));
            when(userTranscriptRepository.findByIdAndUserId(transcriptId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addTranscript(userId, collectionId, transcriptId))
                .isInstanceOf(TranscriptNotFoundException.class);
        }

        @Test
        @DisplayName("is an idempotent no-op when already a member")
        void idempotentReAdd() {
            UserTranscriptEntity transcript = new UserTranscriptEntity();
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));
            when(userTranscriptRepository.findByIdAndUserId(transcriptId, userId)).thenReturn(Optional.of(transcript));
            when(joinRepository.existsByCollection_IdAndUserTranscript_Id(collectionId, transcriptId)).thenReturn(true);

            service.addTranscript(userId, collectionId, transcriptId);

            verify(joinRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws 422 when the collection is at the 5000-member cap")
        void rejectsWhenFull() {
            UserTranscriptEntity transcript = new UserTranscriptEntity();
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));
            when(userTranscriptRepository.findByIdAndUserId(transcriptId, userId)).thenReturn(Optional.of(transcript));
            when(joinRepository.existsByCollection_IdAndUserTranscript_Id(collectionId, transcriptId)).thenReturn(false);
            when(joinRepository.countByCollection_Id(collectionId)).thenReturn(5000L);

            assertThatThrownBy(() -> service.addTranscript(userId, collectionId, transcriptId))
                .isInstanceOf(CollectionLimitExceededException.class);
            verify(joinRepository, never()).save(any());
        }

        @Test
        @DisplayName("appends at position MAX+1 when adding a new member")
        void appendsAtMaxPlusOne() {
            UserTranscriptEntity transcript = new UserTranscriptEntity();
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));
            when(userTranscriptRepository.findByIdAndUserId(transcriptId, userId)).thenReturn(Optional.of(transcript));
            when(joinRepository.existsByCollection_IdAndUserTranscript_Id(collectionId, transcriptId)).thenReturn(false);
            when(joinRepository.countByCollection_Id(collectionId)).thenReturn(2L);
            when(joinRepository.findMaxPosition(collectionId)).thenReturn(4);

            service.addTranscript(userId, collectionId, transcriptId);

            org.mockito.ArgumentCaptor<CollectionTranscriptEntity> captor =
                org.mockito.ArgumentCaptor.forClass(CollectionTranscriptEntity.class);
            verify(joinRepository).save(captor.capture());
            assertThat(captor.getValue().getPosition()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("removeTranscript")
    class RemoveTranscript {

        @Test
        @DisplayName("is a no-op delete-by-key when the transcript is not a member")
        void noOpWhenNotMember() {
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));

            service.removeTranscript(userId, collectionId, transcriptId);

            verify(joinRepository).deleteByCollection_IdAndUserTranscript_Id(collectionId, transcriptId);
        }
    }

    @Nested
    @DisplayName("reorderTranscripts")
    class ReorderTranscripts {

        @Test
        @DisplayName("is a no-op for an empty array")
        void emptyIsNoOp() {
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));

            service.reorderTranscripts(userId, collectionId, List.of());

            verify(joinRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("throws 400 when a supplied id is not a member")
        void rejectsUnknownId() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            CollectionTranscriptEntity rowA = rowFor(a, 0);
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));
            when(joinRepository.findByCollection_IdAndUserTranscript_IdIn(collectionId, List.of(a, b)))
                .thenReturn(List.of(rowA)); // b missing → unknown

            assertThatThrownBy(() -> service.reorderTranscripts(userId, collectionId, List.of(a, b)))
                .isInstanceOf(IllegalArgumentException.class);
            verify(joinRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("assigns contiguous positions from the min current position among supplied ids")
        void assignsContiguousFromMin() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            CollectionTranscriptEntity rowA = rowFor(a, 5);
            CollectionTranscriptEntity rowB = rowFor(b, 2);
            when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));
            when(joinRepository.findByCollection_IdAndUserTranscript_IdIn(collectionId, List.of(a, b)))
                .thenReturn(List.of(rowA, rowB));

            service.reorderTranscripts(userId, collectionId, List.of(a, b));

            assertThat(rowA.getPosition()).isEqualTo(2); // min(5,2) = 2, a first
            assertThat(rowB.getPosition()).isEqualTo(3);
            verify(joinRepository).saveAll(any());
        }

        private CollectionTranscriptEntity rowFor(UUID transcriptId, int position) {
            UserTranscriptEntity ut = new UserTranscriptEntity();
            ut.setId(transcriptId);
            CollectionTranscriptEntity row = new CollectionTranscriptEntity();
            row.setUserTranscript(ut);
            row.setPosition(position);
            return row;
        }
    }

    @Nested
    @DisplayName("collectionIdsContaining")
    class CollectionIdsContaining {

        @Test
        @DisplayName("returns the set of collection ids that contain the transcript")
        void returnsContainingIds() {
            UUID c1 = UUID.randomUUID();
            UUID c2 = UUID.randomUUID();
            when(joinRepository.findCollectionIdsContaining(userId, transcriptId)).thenReturn(List.of(c1, c2));

            assertThat(service.collectionIdsContaining(userId, transcriptId)).containsExactlyInAnyOrder(c1, c2);
        }
    }
}
```

> The `rowFor` helper assumes `UserTranscriptEntity` and `CollectionTranscriptEntity` expose `setId(...)`/`setUserTranscript(...)`/`setPosition(...)` setters from Tasks 2–3 — they do.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=CollectionServiceTest`
Expected: FAILS to compile/run only if `CollectionService` (Task 10) is absent or wrong; otherwise it should pass against the Task 10 implementation. If a specific assertion fails, fix `CollectionService`, not the test.

- [ ] **Step 3: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=CollectionServiceTest`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/app/categorise/domain/service/CollectionServiceTest.java
git commit -m "test: add CollectionService unit tests"
```

---

## Chunk 5: `CollectionController`

### Task 12: `AddCollectionTranscriptRequest` DTO + `CollectionController`

**Files:**
- Create: `src/main/java/com/app/categorise/api/dto/AddCollectionTranscriptRequest.java`
- Create: `src/main/java/com/app/categorise/api/controller/CollectionController.java`

The add endpoint takes a body `{ "userTranscriptId": "..." }` (a new `AddCollectionTranscriptRequest` record). Reorder reuses the `ReorderCollectionRequest` record from Task 8 (`{ "userTranscriptIds": [...] }`). Create/update reuse `CreateCollectionRequest`/`UpdateCollectionRequest` from Task 8. Controller mirrors `CategoryController`: `@RestController @RequestMapping("/api/v1")`, `@AuthenticationPrincipal UserPrincipal` + the private `requireUser(...)` helper. Validated by Task 13.

- [ ] **Step 1: Write `AddCollectionTranscriptRequest`**

```java
package com.app.categorise.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddCollectionTranscriptRequest(
    @NotNull UUID userTranscriptId
) {}
```

- [ ] **Step 2: Write the controller**

```java
package com.app.categorise.api.controller;

import com.app.categorise.api.dto.AddCollectionTranscriptRequest;
import com.app.categorise.api.dto.CollectionDto;
import com.app.categorise.api.dto.CreateCollectionRequest;
import com.app.categorise.api.dto.ReorderCollectionRequest;
import com.app.categorise.api.dto.TranscriptPageResponse;
import com.app.categorise.api.dto.UpdateCollectionRequest;
import com.app.categorise.application.mapper.CollectionMapper;
import com.app.categorise.data.entity.CollectionEntity;
import com.app.categorise.domain.service.CollectionService;
import com.app.categorise.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CollectionController {

    private final CollectionService collectionService;
    private final CollectionMapper collectionMapper;

    public CollectionController(CollectionService collectionService,
                               CollectionMapper collectionMapper) {
        this.collectionService = collectionService;
        this.collectionMapper = collectionMapper;
    }

    @PostMapping("/collections")
    public ResponseEntity<CollectionDto> createCollection(
        @Valid @RequestBody CreateCollectionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        CollectionEntity entity = collectionService.createCollection(
            userId, request.name(), request.description());
        return ResponseEntity.ok(collectionMapper.toDto(entity, 0L));
    }

    @GetMapping("/collections")
    public ResponseEntity<List<CollectionDto>> listCollections(
        @RequestParam(required = false) UUID transcriptId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        Map<UUID, Long> counts = collectionService.memberCounts(userId);
        Set<UUID> containing = transcriptId == null
            ? null
            : collectionService.collectionIdsContaining(userId, transcriptId);

        List<CollectionDto> response = collectionService.listCollections(userId).stream()
            .map(entity -> {
                long count = counts.getOrDefault(entity.getId(), 0L);
                Boolean contains = containing == null ? null : containing.contains(entity.getId());
                return collectionMapper.toDto(entity, count, contains);
            })
            .sorted(Comparator.comparing(CollectionDto::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/collections/{id}")
    public ResponseEntity<CollectionDto> updateCollection(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCollectionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        CollectionEntity entity = collectionService.updateCollection(
            userId, id, request.name(), request.description());
        long count = collectionService.memberCounts(userId).getOrDefault(entity.getId(), 0L);
        return ResponseEntity.ok(collectionMapper.toDto(entity, count));
    }

    @DeleteMapping("/collections/{id}")
    public ResponseEntity<Void> deleteCollection(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        collectionService.deleteCollection(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/collections/{id}/transcripts")
    public ResponseEntity<Void> addTranscript(
        @PathVariable UUID id,
        @Valid @RequestBody AddCollectionTranscriptRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        collectionService.addTranscript(userId, id, request.userTranscriptId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/collections/{id}/transcripts/{userTranscriptId}")
    public ResponseEntity<Void> removeTranscript(
        @PathVariable UUID id,
        @PathVariable UUID userTranscriptId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        collectionService.removeTranscript(userId, id, userTranscriptId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/collections/{id}/transcripts")
    public ResponseEntity<TranscriptPageResponse> listTranscripts(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        TranscriptPageResponse response = collectionService.listTranscripts(
            userId,
            id,
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100)
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/collections/{id}/order")
    public ResponseEntity<Void> reorderTranscripts(
        @PathVariable UUID id,
        @RequestBody ReorderCollectionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        collectionService.reorderTranscripts(userId, id, request.userTranscriptIds());
        return ResponseEntity.noContent().build();
    }

    private UUID requireUser(UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        return principal.getId();
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/app/categorise/api/dto/AddCollectionTranscriptRequest.java src/main/java/com/app/categorise/api/controller/CollectionController.java
git commit -m "feat: add CollectionController and add-transcript request DTO"
```

---

### Task 13: `CollectionControllerTest` (`@WebMvcTest`)

**Files:**
- Test: `src/test/java/com/app/categorise/api/controller/CollectionControllerTest.java`

Mirrors `TranscriptControllerTest`: `@WebMvcTest` excluding `SecurityAutoConfiguration`, `@MockitoBean CollectionService` + `@MockitoBean CollectionMapper`, and a `RequestPostProcessor` that puts a `UserPrincipal` into the `SecurityContext`. One `@Nested` layer per endpoint, covering 200/204/422/404/400 and the `contains` variant. Exception→status mapping comes from `GlobalExceptionHandler` (Task 7) and Bean Validation.

- [ ] **Step 1: Write the failing test**

```java
package com.app.categorise.api.controller;

import com.app.categorise.api.dto.CollectionDto;
import com.app.categorise.api.dto.TranscriptPageResponse;
import com.app.categorise.application.mapper.CollectionMapper;
import com.app.categorise.data.entity.CollectionEntity;
import com.app.categorise.domain.service.CollectionService;
import com.app.categorise.exception.CollectionLimitExceededException;
import com.app.categorise.exception.CollectionNotFoundException;
import com.app.categorise.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CollectionController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
class CollectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CollectionService collectionService;

    @MockitoBean
    private CollectionMapper collectionMapper;

    private UUID userId;
    private UUID collectionId;
    private UUID transcriptId;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        transcriptId = UUID.randomUUID();
        userPrincipal = new UserPrincipal(
            userId, "Test User", "test@example.com", "test@example.com", null, Collections.emptyList());
    }

    private RequestPostProcessor authenticated() {
        return request -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList()));
            SecurityContextHolder.setContext(context);
            request.setUserPrincipal(context.getAuthentication());
            return request;
        };
    }

    private CollectionEntity sampleEntity() {
        CollectionEntity entity = new CollectionEntity(userId, "Favourites", null);
        entity.setId(collectionId);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    @Nested
    @DisplayName("POST /collections")
    class CreateCollection {

        @Test
        @DisplayName("returns 200 with the created collection")
        void createsCollection() throws Exception {
            CollectionEntity entity = sampleEntity();
            when(collectionService.createCollection(eq(userId), eq("Favourites"), any()))
                .thenReturn(entity);
            when(collectionMapper.toDto(eq(entity), anyLong()))
                .thenReturn(new CollectionDto(collectionId, "Favourites", null, 0L,
                    entity.getCreatedAt(), entity.getUpdatedAt(), null));

            mockMvc.perform(post("/api/v1/collections")
                    .with(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Favourites\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Favourites"))
                .andExpect(jsonPath("$.transcriptCount").value(0));
        }

        @Test
        @DisplayName("returns 400 when name is blank (Bean Validation)")
        void rejectsBlankName() throws Exception {
            mockMvc.perform(post("/api/v1/collections")
                    .with(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 422 when the user is at the collection cap")
        void rejectsWhenAtCap() throws Exception {
            when(collectionService.createCollection(eq(userId), any(), any()))
                .thenThrow(new CollectionLimitExceededException("Collection limit reached"));

            mockMvc.perform(post("/api/v1/collections")
                    .with(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"New\"}"))
                .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("GET /collections")
    class ListCollections {

        @Test
        @DisplayName("returns collections with counts and no contains flag")
        void listsWithoutContains() throws Exception {
            CollectionEntity entity = sampleEntity();
            when(collectionService.memberCounts(userId)).thenReturn(Map.of(collectionId, 3L));
            when(collectionService.listCollections(userId)).thenReturn(List.of(entity));
            when(collectionMapper.toDto(eq(entity), eq(3L), eq(null)))
                .thenReturn(new CollectionDto(collectionId, "Favourites", null, 3L,
                    entity.getCreatedAt(), entity.getUpdatedAt(), null));

            mockMvc.perform(get("/api/v1/collections").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transcriptCount").value(3))
                .andExpect(jsonPath("$[0].contains").doesNotExist());
        }

        @Test
        @DisplayName("includes the contains flag when ?transcriptId= is supplied")
        void listsWithContains() throws Exception {
            CollectionEntity entity = sampleEntity();
            when(collectionService.memberCounts(userId)).thenReturn(Map.of(collectionId, 3L));
            when(collectionService.collectionIdsContaining(userId, transcriptId)).thenReturn(Set.of(collectionId));
            when(collectionService.listCollections(userId)).thenReturn(List.of(entity));
            when(collectionMapper.toDto(eq(entity), eq(3L), eq(Boolean.TRUE)))
                .thenReturn(new CollectionDto(collectionId, "Favourites", null, 3L,
                    entity.getCreatedAt(), entity.getUpdatedAt(), true));

            mockMvc.perform(get("/api/v1/collections")
                    .param("transcriptId", transcriptId.toString())
                    .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contains").value(true));
        }
    }

    @Nested
    @DisplayName("PATCH /collections/{id}")
    class UpdateCollection {

        @Test
        @DisplayName("returns 404 when the collection is not owned")
        void notFound() throws Exception {
            when(collectionService.updateCollection(eq(userId), eq(collectionId), any(), any()))
                .thenThrow(new CollectionNotFoundException("Collection not found"));

            mockMvc.perform(patch("/api/v1/collections/" + collectionId)
                    .with(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /collections/{id}")
    class DeleteCollection {

        @Test
        @DisplayName("returns 204")
        void deletes() throws Exception {
            mockMvc.perform(delete("/api/v1/collections/" + collectionId).with(authenticated()))
                .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /collections/{id}/transcripts")
    class AddTranscript {

        @Test
        @DisplayName("returns 200 on add")
        void adds() throws Exception {
            mockMvc.perform(post("/api/v1/collections/" + collectionId + "/transcripts")
                    .with(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userTranscriptId\":\"" + transcriptId + "\"}"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("returns 422 when the collection is full")
        void rejectsWhenFull() throws Exception {
            doThrow(new CollectionLimitExceededException("Collection is full"))
                .when(collectionService).addTranscript(userId, collectionId, transcriptId);

            mockMvc.perform(post("/api/v1/collections/" + collectionId + "/transcripts")
                    .with(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userTranscriptId\":\"" + transcriptId + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("DELETE /collections/{id}/transcripts/{userTranscriptId}")
    class RemoveTranscript {

        @Test
        @DisplayName("returns 204 (idempotent)")
        void removes() throws Exception {
            mockMvc.perform(delete("/api/v1/collections/" + collectionId + "/transcripts/" + transcriptId)
                    .with(authenticated()))
                .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("GET /collections/{id}/transcripts")
    class ListTranscripts {

        @Test
        @DisplayName("returns the paginated member page")
        void listsMembers() throws Exception {
            when(collectionService.listTranscripts(eq(userId), eq(collectionId), eq(0), eq(50)))
                .thenReturn(new TranscriptPageResponse(List.of(), 0, 50, 0L, 0, false));

            mockMvc.perform(get("/api/v1/collections/" + collectionId + "/transcripts").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.page").value(0));
        }

        @Test
        @DisplayName("returns 404 when the collection is not owned")
        void notFound() throws Exception {
            when(collectionService.listTranscripts(eq(userId), eq(collectionId), eq(0), eq(50)))
                .thenThrow(new CollectionNotFoundException("Collection not found"));

            mockMvc.perform(get("/api/v1/collections/" + collectionId + "/transcripts").with(authenticated()))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /collections/{id}/order")
    class ReorderTranscripts {

        @Test
        @DisplayName("returns 204 on a valid reorder")
        void reorders() throws Exception {
            mockMvc.perform(patch("/api/v1/collections/" + collectionId + "/order")
                    .with(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userTranscriptIds\":[\"" + transcriptId + "\"]}"))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("returns 400 when an id is not a member")
        void rejectsUnknownId() throws Exception {
            doThrow(new IllegalArgumentException("Reorder contains 1 id(s) that are not members"))
                .when(collectionService).reorderTranscripts(eq(userId), eq(collectionId), any());

            mockMvc.perform(patch("/api/v1/collections/" + collectionId + "/order")
                    .with(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userTranscriptIds\":[\"" + transcriptId + "\"]}"))
                .andExpect(status().isBadRequest());
        }
    }
}
```

> `IllegalArgumentException → 400` is already mapped by the existing `GlobalExceptionHandler` (same path that returns 400 for subcategory validation errors). If that handler maps `IllegalArgumentException` to a different status, adjust the expectation to match the existing global mapping rather than changing the controller.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=CollectionControllerTest`
Expected: FAILS only if controller wiring/DTOs are wrong; otherwise passes against Task 12.

- [ ] **Step 3: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=CollectionControllerTest`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/app/categorise/api/controller/CollectionControllerTest.java
git commit -m "test: add CollectionController WebMvc tests"
```

---

## Chunk 6: iOS (Scoop) — model & service

> All iOS work happens in the `/Users/apushpavannan/personal/TranscribeAssistant-ios` repo. New `.swift` files are auto-compiled (see Conventions). The iOS test command from Conventions is used in every step below.

### Task 14: `CollectionResponse` Codable model

**Files:**
- Create: `Scoop/models/api/CollectionResponse.swift`
- Test: `ScoopTests/CollectionResponseTests.swift`

Mirrors `CollectionDto` (Task 8): `id`, `name`, `description?`, `transcriptCount`, `createdAt`, `updatedAt`, and the nullable `contains`. Backend ids are UUID JSON strings; decode `id` as `String` like `SubcategoryResponse`.

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Scoop

extension ScoopTestSuiteContainer {
@Suite(.serialized)
struct CollectionResponseTests {

    @Test func decodes_full_payload_with_contains() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Weeknight dinners",
          "description": "quick meals",
          "transcriptCount": 4,
          "createdAt": "2026-06-26T10:00:00Z",
          "updatedAt": "2026-06-26T11:00:00Z",
          "contains": true
        }
        """.data(using: .utf8)!

        let model = try JSONDecoder().decode(CollectionResponse.self, from: json)

        #expect(model.id == "11111111-1111-1111-1111-111111111111")
        #expect(model.name == "Weeknight dinners")
        #expect(model.description == "quick meals")
        #expect(model.transcriptCount == 4)
        #expect(model.contains == true)
    }

    @Test func decodes_when_contains_and_description_absent() throws {
        let json = """
        {
          "id": "22222222-2222-2222-2222-222222222222",
          "name": "Reading list",
          "transcriptCount": 0,
          "createdAt": "2026-06-26T10:00:00Z",
          "updatedAt": "2026-06-26T10:00:00Z"
        }
        """.data(using: .utf8)!

        let model = try JSONDecoder().decode(CollectionResponse.self, from: json)

        #expect(model.description == nil)
        #expect(model.contains == nil)
        #expect(model.transcriptCount == 0)
    }
}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionResponseTests
```
Expected: FAILS to compile — `CollectionResponse` does not exist yet.

- [ ] **Step 3: Write `CollectionResponse`**

```swift
import Foundation

struct CollectionResponse: Codable, Identifiable {
    let id: String
    let name: String
    let description: String?
    let transcriptCount: Int
    let createdAt: String
    let updatedAt: String
    let contains: Bool?
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionResponseTests
```
Expected: TEST SUCCEEDED.

- [ ] **Step 5: Commit**

```bash
git add Scoop/models/api/CollectionResponse.swift ScoopTests/CollectionResponseTests.swift
git commit -m "feat: add CollectionResponse model with decoding tests"
```

---

### Task 15: `CollectionService` over `HTTPClient`

**Files:**
- Create: `Scoop/models/api/TranscriptPageResponse.swift` (page wrapper for member listing — none exists yet)
- Create: `Scoop/service/CollectionService.swift`
- Test: `ScoopTests/CollectionServiceTests.swift`

`CollectionService` mirrors `CategoryService`: `static var client: HTTPClient!`, `configure(client:)`, and static methods that call `client.request`/`client.dataRequest` and map `HTTPError → NetworkError`. Methods: `getCollections`, `listForTranscript(userTranscriptId:)` (the `?transcriptId=` variant, surfaces `contains`), `createCollection`, `renameCollection` (PATCH), `deleteCollection`, `addTranscript(collectionId:userTranscriptId:)`, `removeTranscript(collectionId:userTranscriptId:)`, `getCollectionTranscripts(collectionId:page:size:)` (decodes `TranscriptPageResponse`), `reorderTranscripts(collectionId:userTranscriptIds:)`. Duplicate-name `HTTPError.conflict` is surfaced as `NetworkError.requestFailed` (matching the existing services' `default:` mapping). Tests assert path/query/body and decode via `MockURLProtocol`.

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Scoop

extension ScoopTestSuiteContainer {
@Suite(.serialized)
struct CollectionServiceTests {
    private func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        return URLSession(configuration: config)
    }

    @Test func getCollections_success() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)

        MockURLProtocol.requestHandler = { req in
            #expect(req.url?.path == "/api/v1/collections")
            #expect(req.httpMethod == "GET")
            let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type":"application/json"])!
            let item: [String: Any] = [
                "id": "11111111-1111-1111-1111-111111111111",
                "name": "Dinners",
                "transcriptCount": 2,
                "createdAt": "2026-06-26T10:00:00Z",
                "updatedAt": "2026-06-26T10:00:00Z"
            ]
            let data = try JSONSerialization.data(withJSONObject: [item])
            return (resp, data)
        }

        let result: Result<[CollectionResponse], NetworkError> = await withCheckedContinuation { cont in
            CollectionService.getCollections { res in cont.resume(returning: res) }
        }
        switch result {
        case .success(let list):
            #expect(list.count == 1)
            #expect(list[0].name == "Dinners")
        case .failure(let err):
            Issue.record("Unexpected failure: \(err)")
        }
        MockURLProtocol.requestHandler = nil
    }

    @Test func listForTranscript_sends_query_and_decodes_contains() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)
        let transcriptId = UUID()

        MockURLProtocol.requestHandler = { req in
            #expect(req.url?.path == "/api/v1/collections")
            let components = URLComponents(url: req.url!, resolvingAgainstBaseURL: false)
            let queried = components?.queryItems?.first(where: { $0.name == "transcriptId" })?.value
            #expect(queried == transcriptId.uuidString)
            let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type":"application/json"])!
            let item: [String: Any] = [
                "id": "11111111-1111-1111-1111-111111111111",
                "name": "Dinners",
                "transcriptCount": 2,
                "createdAt": "2026-06-26T10:00:00Z",
                "updatedAt": "2026-06-26T10:00:00Z",
                "contains": true
            ]
            let data = try JSONSerialization.data(withJSONObject: [item])
            return (resp, data)
        }

        let result: Result<[CollectionResponse], NetworkError> = await withCheckedContinuation { cont in
            CollectionService.listForTranscript(userTranscriptId: transcriptId) { res in cont.resume(returning: res) }
        }
        switch result {
        case .success(let list):
            #expect(list[0].contains == true)
        case .failure(let err):
            Issue.record("Unexpected failure: \(err)")
        }
        MockURLProtocol.requestHandler = nil
    }

    @Test func createCollection_posts_name_body() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)

        MockURLProtocol.requestHandler = { req in
            #expect(req.url?.path == "/api/v1/collections")
            #expect(req.httpMethod == "POST")
            let bodyData = req.httpBodyStreamData() ?? req.httpBody ?? Data()
            let body = try JSONSerialization.jsonObject(with: bodyData) as? [String: Any]
            #expect(body?["name"] as? String == "New list")
            let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type":"application/json"])!
            let item: [String: Any] = [
                "id": "11111111-1111-1111-1111-111111111111",
                "name": "New list",
                "transcriptCount": 0,
                "createdAt": "2026-06-26T10:00:00Z",
                "updatedAt": "2026-06-26T10:00:00Z"
            ]
            let data = try JSONSerialization.data(withJSONObject: item)
            return (resp, data)
        }

        let result: Result<CollectionResponse, NetworkError> = await withCheckedContinuation { cont in
            CollectionService.createCollection(name: "New list", description: nil) { res in cont.resume(returning: res) }
        }
        switch result {
        case .success(let model):
            #expect(model.name == "New list")
        case .failure(let err):
            Issue.record("Unexpected failure: \(err)")
        }
        MockURLProtocol.requestHandler = nil
    }

    @Test func addTranscript_posts_to_collection_transcripts() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)
        let collectionId = UUID()
        let transcriptId = UUID()

        MockURLProtocol.requestHandler = { req in
            #expect(req.url?.path == "/api/v1/collections/\(collectionId.uuidString)/transcripts")
            #expect(req.httpMethod == "POST")
            let bodyData = req.httpBodyStreamData() ?? req.httpBody ?? Data()
            let body = try JSONSerialization.jsonObject(with: bodyData) as? [String: Any]
            #expect(body?["userTranscriptId"] as? String == transcriptId.uuidString)
            let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (resp, Data())
        }

        let result: Result<Void, NetworkError> = await withCheckedContinuation { cont in
            CollectionService.addTranscript(collectionId: collectionId, userTranscriptId: transcriptId) { res in
                cont.resume(returning: res)
            }
        }
        switch result {
        case .success: break
        case .failure(let err): Issue.record("Unexpected failure: \(err)")
        }
        MockURLProtocol.requestHandler = nil
    }

    @Test func reorderTranscripts_patches_order_with_id_array() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)
        let collectionId = UUID()
        let a = UUID()
        let b = UUID()

        MockURLProtocol.requestHandler = { req in
            #expect(req.url?.path == "/api/v1/collections/\(collectionId.uuidString)/order")
            #expect(req.httpMethod == "PATCH")
            let bodyData = req.httpBodyStreamData() ?? req.httpBody ?? Data()
            let body = try JSONSerialization.jsonObject(with: bodyData) as? [String: Any]
            let ids = body?["userTranscriptIds"] as? [String]
            #expect(ids == [a.uuidString, b.uuidString])
            let resp = HTTPURLResponse(url: req.url!, statusCode: 204, httpVersion: nil, headerFields: nil)!
            return (resp, Data())
        }

        let result: Result<Void, NetworkError> = await withCheckedContinuation { cont in
            CollectionService.reorderTranscripts(collectionId: collectionId, userTranscriptIds: [a, b]) { res in
                cont.resume(returning: res)
            }
        }
        switch result {
        case .success: break
        case .failure(let err): Issue.record("Unexpected failure: \(err)")
        }
        MockURLProtocol.requestHandler = nil
    }

    @Test func getCollectionTranscripts_decodes_page() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)
        let collectionId = UUID()

        MockURLProtocol.requestHandler = { req in
            #expect(req.url?.path == "/api/v1/collections/\(collectionId.uuidString)/transcripts")
            let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type":"application/json"])!
            let item: [String: Any] = [
                "id": "id-1", "videoUrl": "", "transcript": "", "title": "A", "duration": 0,
                "uploadedAt": "", "createdAt": ""
            ]
            let page: [String: Any] = [
                "items": [item], "page": 0, "size": 50, "totalItems": 1, "totalPages": 1, "hasNext": false
            ]
            let data = try JSONSerialization.data(withJSONObject: page)
            return (resp, data)
        }

        let result: Result<TranscriptPageResponse, NetworkError> = await withCheckedContinuation { cont in
            CollectionService.getCollectionTranscripts(collectionId: collectionId, page: 0, size: 50) { res in
                cont.resume(returning: res)
            }
        }
        switch result {
        case .success(let pageModel):
            #expect(pageModel.totalItems == 1)
            #expect(pageModel.items.first?.title == "A")
        case .failure(let err):
            Issue.record("Unexpected failure: \(err)")
        }
        MockURLProtocol.requestHandler = nil
    }
}
}

// Helper to read a body that URLSession may have converted to a stream.
private extension URLRequest {
    func httpBodyStreamData() -> Data? {
        guard let stream = httpBodyStream else { return nil }
        stream.open()
        defer { stream.close() }
        var data = Data()
        let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }
        while stream.hasBytesAvailable {
            let read = stream.read(buffer, maxLength: bufferSize)
            if read <= 0 { break }
            data.append(buffer, count: read)
        }
        return data.isEmpty ? nil : data
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionServiceTests
```
Expected: FAILS to compile — `CollectionService` and `TranscriptPageResponse` do not exist yet.

- [ ] **Step 3: Write the page wrapper model**

```swift
import Foundation

struct TranscriptPageResponse: Codable {
    let items: [TranscriptResponse]
    let page: Int
    let size: Int
    let totalItems: Int
    let totalPages: Int
    let hasNext: Bool
}
```

- [ ] **Step 4: Write `CollectionService`**

```swift
import Foundation

class CollectionService {
    struct CreateCollectionRequest: Codable { let name: String; let description: String? }
    struct UpdateCollectionRequest: Codable { let name: String?; let description: String? }
    struct AddCollectionTranscriptRequest: Codable { let userTranscriptId: String }
    struct ReorderCollectionRequest: Codable { let userTranscriptIds: [String] }

    static var client: HTTPClient!

    static func configure(client: HTTPClient) {
        self.client = client
    }

    private static func map(_ httpErr: HTTPError) -> NetworkError {
        switch httpErr {
        case .decoding: return .decodingError
        case .invalidURL: return .invalidURL
        default: return .requestFailed
        }
    }

    static func getCollections(completion: @escaping (Result<[CollectionResponse], NetworkError>) -> Void) {
        Task {
            do {
                guard let client = Self.client else { completion(.failure(.requestFailed)); return }
                let response: [CollectionResponse] = try await client.request(path: "/api/v1/collections", method: .get)
                completion(.success(response))
            } catch let httpErr as HTTPError {
                completion(.failure(map(httpErr)))
            } catch {
                completion(.failure(.requestFailed))
            }
        }
    }

    static func listForTranscript(userTranscriptId: UUID,
                                  completion: @escaping (Result<[CollectionResponse], NetworkError>) -> Void) {
        Task {
            do {
                guard let client = Self.client else { completion(.failure(.requestFailed)); return }
                let path = "/api/v1/collections?transcriptId=\(userTranscriptId.uuidString)"
                let response: [CollectionResponse] = try await client.request(path: path, method: .get)
                completion(.success(response))
            } catch let httpErr as HTTPError {
                completion(.failure(map(httpErr)))
            } catch {
                completion(.failure(.requestFailed))
            }
        }
    }

    static func createCollection(name: String, description: String?,
                                 completion: @escaping (Result<CollectionResponse, NetworkError>) -> Void) {
        Task {
            do {
                guard let client = Self.client else { completion(.failure(.requestFailed)); return }
                let body = try JSONEncoder().encode(CreateCollectionRequest(name: name, description: description))
                let response: CollectionResponse = try await client.request(
                    path: "/api/v1/collections", method: .post,
                    headers: ["Content-Type": "application/json"], body: body)
                completion(.success(response))
            } catch let httpErr as HTTPError {
                completion(.failure(map(httpErr)))
            } catch {
                completion(.failure(.requestFailed))
            }
        }
    }

    static func renameCollection(collectionId: UUID, name: String?, description: String?,
                                 completion: @escaping (Result<CollectionResponse, NetworkError>) -> Void) {
        Task {
            do {
                guard let client = Self.client else { completion(.failure(.requestFailed)); return }
                let body = try JSONEncoder().encode(UpdateCollectionRequest(name: name, description: description))
                let response: CollectionResponse = try await client.request(
                    path: "/api/v1/collections/\(collectionId.uuidString)", method: .patch,
                    headers: ["Content-Type": "application/json"], body: body)
                completion(.success(response))
            } catch let httpErr as HTTPError {
                completion(.failure(map(httpErr)))
            } catch {
                completion(.failure(.requestFailed))
            }
        }
    }

    static func deleteCollection(collectionId: UUID,
                                 completion: @escaping (Result<Void, NetworkError>) -> Void) {
        Task {
            do {
                guard let client = Self.client else { completion(.failure(.requestFailed)); return }
                try await client.dataRequest(path: "/api/v1/collections/\(collectionId.uuidString)", method: .delete)
                completion(.success(()))
            } catch let httpErr as HTTPError {
                completion(.failure(map(httpErr)))
            } catch {
                completion(.failure(.requestFailed))
            }
        }
    }

    static func addTranscript(collectionId: UUID, userTranscriptId: UUID,
                              completion: @escaping (Result<Void, NetworkError>) -> Void) {
        Task {
            do {
                guard let client = Self.client else { completion(.failure(.requestFailed)); return }
                let body = try JSONEncoder().encode(
                    AddCollectionTranscriptRequest(userTranscriptId: userTranscriptId.uuidString))
                try await client.dataRequest(
                    path: "/api/v1/collections/\(collectionId.uuidString)/transcripts", method: .post,
                    headers: ["Content-Type": "application/json"], body: body)
                completion(.success(()))
            } catch let httpErr as HTTPError {
                completion(.failure(map(httpErr)))
            } catch {
                completion(.failure(.requestFailed))
            }
        }
    }

    static func removeTranscript(collectionId: UUID, userTranscriptId: UUID,
                                 completion: @escaping (Result<Void, NetworkError>) -> Void) {
        Task {
            do {
                guard let client = Self.client else { completion(.failure(.requestFailed)); return }
                let path = "/api/v1/collections/\(collectionId.uuidString)/transcripts/\(userTranscriptId.uuidString)"
                try await client.dataRequest(path: path, method: .delete)
                completion(.success(()))
            } catch let httpErr as HTTPError {
                completion(.failure(map(httpErr)))
            } catch {
                completion(.failure(.requestFailed))
            }
        }
    }

    static func getCollectionTranscripts(collectionId: UUID, page: Int, size: Int,
                                         completion: @escaping (Result<TranscriptPageResponse, NetworkError>) -> Void) {
        Task {
            do {
                guard let client = Self.client else { completion(.failure(.requestFailed)); return }
                let path = "/api/v1/collections/\(collectionId.uuidString)/transcripts?page=\(page)&size=\(size)"
                let response: TranscriptPageResponse = try await client.request(path: path, method: .get)
                completion(.success(response))
            } catch let httpErr as HTTPError {
                completion(.failure(map(httpErr)))
            } catch {
                completion(.failure(.requestFailed))
            }
        }
    }

    static func reorderTranscripts(collectionId: UUID, userTranscriptIds: [UUID],
                                   completion: @escaping (Result<Void, NetworkError>) -> Void) {
        Task {
            do {
                guard let client = Self.client else { completion(.failure(.requestFailed)); return }
                let body = try JSONEncoder().encode(
                    ReorderCollectionRequest(userTranscriptIds: userTranscriptIds.map { $0.uuidString }))
                try await client.dataRequest(
                    path: "/api/v1/collections/\(collectionId.uuidString)/order", method: .patch,
                    headers: ["Content-Type": "application/json"], body: body)
                completion(.success(()))
            } catch let httpErr as HTTPError {
                completion(.failure(map(httpErr)))
            } catch {
                completion(.failure(.requestFailed))
            }
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionServiceTests
```
Expected: TEST SUCCEEDED.

- [ ] **Step 6: Commit**

```bash
git add Scoop/models/api/TranscriptPageResponse.swift Scoop/service/CollectionService.swift ScoopTests/CollectionServiceTests.swift
git commit -m "feat: add iOS CollectionService and TranscriptPageResponse with tests"
```

---

## Chunk 7: iOS (Scoop) — screens

> View models live in `Scoop/viewModel/` (matching the existing `FeedViewModel`/`DashboardViewModel` location), and views under `Scoop/views/Collections/` and `Scoop/views/Feed/`. Member rows reuse `TranscriptCard`, which takes a domain `Transcript`; `[TranscriptResponse].toDomain()` (existing `TranscriptMapper`) converts the page items. View models are `@MainActor` `ObservableObject`s like `FeedViewModel`.

### Task 16: Collections list — `CollectionsViewModel` + `CollectionsScreen`

**Files:**
- Create: `Scoop/viewModel/CollectionsViewModel.swift`
- Create: `Scoop/views/Collections/CollectionsScreen.swift`
- Test: `ScoopTests/CollectionsViewModelTests.swift`

The view model loads collections via `CollectionService.getCollections`, exposes `@Published var collections: [CollectionResponse]`, `isLoading`, `loadError`, and a `createCollection(name:)` that prepends the created collection on success. The screen lists collections with their `transcriptCount`, a create button, and `NavigationLink` into `CollectionDetailScreen`.

- [ ] **Step 1: Write the failing view-model test**

```swift
import Foundation
import Testing
@testable import Scoop

extension ScoopTestSuiteContainer {
@Suite(.serialized)
struct CollectionsViewModelTests {
    private func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        return URLSession(configuration: config)
    }

    @MainActor
    @Test func load_populates_collections() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)
        MockURLProtocol.requestHandler = { req in
            let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type":"application/json"])!
            let item: [String: Any] = [
                "id": "11111111-1111-1111-1111-111111111111",
                "name": "Dinners", "transcriptCount": 2,
                "createdAt": "2026-06-26T10:00:00Z", "updatedAt": "2026-06-26T10:00:00Z"
            ]
            return (resp, try JSONSerialization.data(withJSONObject: [item]))
        }

        let vm = CollectionsViewModel()
        await vm.load()

        #expect(vm.collections.count == 1)
        #expect(vm.collections.first?.name == "Dinners")
        #expect(vm.isLoading == false)
        MockURLProtocol.requestHandler = nil
    }
}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionsViewModelTests
```
Expected: FAILS to compile — `CollectionsViewModel` does not exist yet.

- [ ] **Step 3: Write `CollectionsViewModel`**

```swift
import Foundation
import Combine

@MainActor
class CollectionsViewModel: ObservableObject {
    @Published var collections: [CollectionResponse] = []
    @Published var isLoading = false
    @Published var loadError: String?
    @Published var isCreating = false

    func load() async {
        isLoading = true
        loadError = nil
        let result: Result<[CollectionResponse], NetworkError> = await withCheckedContinuation { cont in
            CollectionService.getCollections { res in cont.resume(returning: res) }
        }
        switch result {
        case .success(let list):
            collections = list.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        case .failure:
            loadError = "Couldn't load collections. Pull to retry."
        }
        isLoading = false
    }

    func createCollection(name: String) async {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        isCreating = true
        let result: Result<CollectionResponse, NetworkError> = await withCheckedContinuation { cont in
            CollectionService.createCollection(name: trimmed, description: nil) { res in cont.resume(returning: res) }
        }
        if case .success(let created) = result {
            collections.insert(created, at: 0)
        }
        isCreating = false
    }
}
```

- [ ] **Step 4: Write `CollectionsScreen`**

```swift
import SwiftUI

struct CollectionsScreen: View {
    @StateObject private var viewModel = CollectionsViewModel()
    @State private var showNewCollectionAlert = false
    @State private var newCollectionName = ""

    var body: some View {
        Group {
            if viewModel.isLoading && viewModel.collections.isEmpty {
                ProgressView().tint(AppColors.scoopPurple)
            } else if let error = viewModel.loadError, viewModel.collections.isEmpty {
                VStack(spacing: 12) {
                    Text(error).foregroundColor(AppColors.secondaryText)
                    Button("Retry") { Task { await viewModel.load() } }
                        .foregroundColor(AppColors.scoopPurple)
                }
            } else {
                List {
                    ForEach(viewModel.collections) { collection in
                        NavigationLink(destination: CollectionDetailScreen(
                            collectionId: UUID(uuidString: collection.id) ?? UUID(),
                            title: collection.name
                        )) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(collection.name)
                                    .font(.system(size: 17, weight: .semibold))
                                    .foregroundColor(AppColors.primaryText)
                                Text("\(collection.transcriptCount) saved")
                                    .font(.system(size: 13))
                                    .foregroundColor(AppColors.secondaryText)
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .refreshable { await viewModel.load() }
            }
        }
        .navigationTitle("Collections")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showNewCollectionAlert = true } label: { Image(systemName: "plus") }
                    .tint(AppColors.scoopPurple)
            }
        }
        .alert("New collection", isPresented: $showNewCollectionAlert) {
            TextField("Name", text: $newCollectionName)
            Button("Cancel", role: .cancel) { newCollectionName = "" }
            Button("Create") {
                let name = newCollectionName
                newCollectionName = ""
                Task { await viewModel.createCollection(name: name) }
            }
        }
        .task { await viewModel.load() }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionsViewModelTests
```
Expected: TEST SUCCEEDED.

- [ ] **Step 6: Commit**

```bash
git add Scoop/viewModel/CollectionsViewModel.swift Scoop/views/Collections/CollectionsScreen.swift ScoopTests/CollectionsViewModelTests.swift
git commit -m "feat: add Collections list screen and view model"
```

---

### Task 17: Collection detail — `CollectionDetailViewModel` + `CollectionDetailScreen` (drag-to-reorder)

**Files:**
- Create: `Scoop/viewModel/CollectionDetailViewModel.swift`
- Create: `Scoop/views/Collections/CollectionDetailScreen.swift`
- Test: `ScoopTests/CollectionDetailViewModelTests.swift`

The view model pages members via `CollectionService.getCollectionTranscripts`, mapping `[TranscriptResponse].toDomain()` into `@Published var transcripts: [Transcript]` (position order preserved from the server). `move(from:to:)` reorders the local array and calls `CollectionService.reorderTranscripts` with the new id order. The screen renders members with `TranscriptCard` inside a `List` with `.onMove` + `EditButton`.

- [ ] **Step 1: Write the failing view-model test**

```swift
import Foundation
import Testing
@testable import Scoop

extension ScoopTestSuiteContainer {
@Suite(.serialized)
struct CollectionDetailViewModelTests {
    private func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        return URLSession(configuration: config)
    }

    private func pageJSON(ids: [String]) -> Data {
        let items: [[String: Any]] = ids.map { id in
            ["id": id, "videoUrl": "", "transcript": "", "title": "T-\(id)", "duration": 0,
             "uploadedAt": "", "createdAt": ""]
        }
        let page: [String: Any] = ["items": items, "page": 0, "size": 50,
                                   "totalItems": items.count, "totalPages": 1, "hasNext": false]
        return try! JSONSerialization.data(withJSONObject: page)
    }

    @MainActor
    @Test func load_populates_ordered_members() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)
        let collectionId = UUID()
        let a = UUID(); let b = UUID()
        MockURLProtocol.requestHandler = { [self] req in
            let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type":"application/json"])!
            return (resp, pageJSON(ids: [a.uuidString, b.uuidString]))
        }

        let vm = CollectionDetailViewModel(collectionId: collectionId)
        await vm.load()

        #expect(vm.transcripts.map(\.id) == [a, b])
        MockURLProtocol.requestHandler = nil
    }

    @MainActor
    @Test func move_reorders_local_array_and_calls_reorder() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)
        let collectionId = UUID()
        let a = UUID(); let b = UUID(); let c = UUID()

        // First request: load. Subsequent request: the PATCH /order call.
        var sawReorderBody: [String]?
        MockURLProtocol.requestHandler = { [self] req in
            if req.url?.path.hasSuffix("/order") == true {
                let bodyData = req.httpBodyStreamData() ?? req.httpBody ?? Data()
                let body = try? JSONSerialization.jsonObject(with: bodyData) as? [String: Any]
                sawReorderBody = body?["userTranscriptIds"] as? [String]
                let resp = HTTPURLResponse(url: req.url!, statusCode: 204, httpVersion: nil, headerFields: nil)!
                return (resp, Data())
            }
            let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type":"application/json"])!
            return (resp, pageJSON(ids: [a.uuidString, b.uuidString, c.uuidString]))
        }

        let vm = CollectionDetailViewModel(collectionId: collectionId)
        await vm.load()
        await vm.move(from: IndexSet(integer: 2), to: 0) // move c to front

        #expect(vm.transcripts.map(\.id) == [c, a, b])
        #expect(sawReorderBody == [c.uuidString, a.uuidString, b.uuidString])
        MockURLProtocol.requestHandler = nil
    }
}

private extension URLRequest {
    func httpBodyStreamData() -> Data? {
        guard let stream = httpBodyStream else { return nil }
        stream.open(); defer { stream.close() }
        var data = Data(); let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }
        while stream.hasBytesAvailable {
            let read = stream.read(buffer, maxLength: bufferSize)
            if read <= 0 { break }
            data.append(buffer, count: read)
        }
        return data.isEmpty ? nil : data
    }
}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionDetailViewModelTests
```
Expected: FAILS to compile — `CollectionDetailViewModel` does not exist yet.

- [ ] **Step 3: Write `CollectionDetailViewModel`**

```swift
import Foundation
import Combine

@MainActor
class CollectionDetailViewModel: ObservableObject {
    @Published var transcripts: [Transcript] = []
    @Published var isLoading = false
    @Published var loadError: String?

    let collectionId: UUID
    private var page = 0
    private let pageSize = 50
    private var hasNext = false

    init(collectionId: UUID) {
        self.collectionId = collectionId
    }

    func load() async {
        isLoading = true
        loadError = nil
        page = 0
        let result = await fetch(page: 0)
        switch result {
        case .success(let response):
            transcripts = response.items.toDomain()
            hasNext = response.hasNext
        case .failure:
            loadError = "Couldn't load this collection. Pull to retry."
        }
        isLoading = false
    }

    func loadMoreIfNeeded(current item: Transcript) async {
        guard hasNext, item.id == transcripts.last?.id else { return }
        let next = page + 1
        if case .success(let response) = await fetch(page: next) {
            transcripts.append(contentsOf: response.items.toDomain())
            page = next
            hasNext = response.hasNext
        }
    }

    func move(from source: IndexSet, to destination: Int) async {
        transcripts.move(fromOffsets: source, toOffset: destination)
        let order = transcripts.map(\.id)
        _ = await withCheckedContinuation { (cont: CheckedContinuation<Result<Void, NetworkError>, Never>) in
            CollectionService.reorderTranscripts(collectionId: collectionId, userTranscriptIds: order) { res in
                cont.resume(returning: res)
            }
        }
    }

    func remove(_ transcript: Transcript) async {
        let result: Result<Void, NetworkError> = await withCheckedContinuation { cont in
            CollectionService.removeTranscript(collectionId: collectionId, userTranscriptId: transcript.id) { res in
                cont.resume(returning: res)
            }
        }
        if case .success = result {
            transcripts.removeAll { $0.id == transcript.id }
        }
    }

    private func fetch(page: Int) async -> Result<TranscriptPageResponse, NetworkError> {
        await withCheckedContinuation { cont in
            CollectionService.getCollectionTranscripts(collectionId: collectionId, page: page, size: pageSize) { res in
                cont.resume(returning: res)
            }
        }
    }
}
```

- [ ] **Step 4: Write `CollectionDetailScreen`**

```swift
import SwiftUI

struct CollectionDetailScreen: View {
    let collectionId: UUID
    let title: String

    @StateObject private var viewModel: CollectionDetailViewModel

    init(collectionId: UUID, title: String) {
        self.collectionId = collectionId
        self.title = title
        _viewModel = StateObject(wrappedValue: CollectionDetailViewModel(collectionId: collectionId))
    }

    var body: some View {
        Group {
            if viewModel.isLoading && viewModel.transcripts.isEmpty {
                ProgressView().tint(AppColors.scoopPurple)
            } else if let error = viewModel.loadError, viewModel.transcripts.isEmpty {
                VStack(spacing: 12) {
                    Text(error).foregroundColor(AppColors.secondaryText)
                    Button("Retry") { Task { await viewModel.load() } }
                        .foregroundColor(AppColors.scoopPurple)
                }
            } else if viewModel.transcripts.isEmpty {
                Text("No transcripts in this collection yet.")
                    .foregroundColor(AppColors.secondaryText)
            } else {
                List {
                    ForEach(viewModel.transcripts) { transcript in
                        TranscriptCard(transcript: transcript)
                            .listRowSeparator(.hidden)
                            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                            .task { await viewModel.loadMoreIfNeeded(current: transcript) }
                    }
                    .onMove { source, destination in
                        Task { await viewModel.move(from: source, to: destination) }
                    }
                    .onDelete { offsets in
                        let toRemove = offsets.map { viewModel.transcripts[$0] }
                        Task { for t in toRemove { await viewModel.remove(t) } }
                    }
                }
                .listStyle(.plain)
                .refreshable { await viewModel.load() }
            }
        }
        .navigationTitle(title)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) { EditButton().tint(AppColors.scoopPurple) }
        }
        .task { await viewModel.load() }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionDetailViewModelTests
```
Expected: TEST SUCCEEDED.

- [ ] **Step 6: Commit**

```bash
git add Scoop/viewModel/CollectionDetailViewModel.swift Scoop/views/Collections/CollectionDetailScreen.swift ScoopTests/CollectionDetailViewModelTests.swift
git commit -m "feat: add Collection detail screen with drag-to-reorder"
```

---

### Task 18: Add-to-collection picker — `CollectionPickerSheet` + Dashboard entry

**Files:**
- Create: `Scoop/viewModel/CollectionPickerViewModel.swift`
- Create: `Scoop/views/Feed/CollectionPickerSheet.swift`
- Modify: `Scoop/views/Dashboard/DashboardScreen.swift` (add a "Collections" `NavigationLink`)
- Test: `ScoopTests/CollectionPickerViewModelTests.swift`

The picker loads collections via `CollectionService.listForTranscript(userTranscriptId:)` so each row's pre-checked state comes from the server `contains` flag (§4.5). Toggling a row calls `addTranscript`/`removeTranscript` and flips local state. The Dashboard gains a "Collections" entry alongside the category browse.

- [ ] **Step 1: Write the failing view-model test**

```swift
import Foundation
import Testing
@testable import Scoop

extension ScoopTestSuiteContainer {
@Suite(.serialized)
struct CollectionPickerViewModelTests {
    private func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        return URLSession(configuration: config)
    }

    @MainActor
    @Test func load_marks_selected_from_contains_flag() async throws {
        let session = makeSession()
        CollectionService.client = HTTPClient(baseURL: URL(string: "https://example.com")!, session: session)
        let transcriptId = UUID()
        let containedId = "11111111-1111-1111-1111-111111111111"
        let otherId = "22222222-2222-2222-2222-222222222222"

        MockURLProtocol.requestHandler = { req in
            let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type":"application/json"])!
            let items: [[String: Any]] = [
                ["id": containedId, "name": "In", "transcriptCount": 1,
                 "createdAt": "2026-06-26T10:00:00Z", "updatedAt": "2026-06-26T10:00:00Z", "contains": true],
                ["id": otherId, "name": "Out", "transcriptCount": 0,
                 "createdAt": "2026-06-26T10:00:00Z", "updatedAt": "2026-06-26T10:00:00Z", "contains": false]
            ]
            return (resp, try JSONSerialization.data(withJSONObject: items))
        }

        let vm = CollectionPickerViewModel(userTranscriptId: transcriptId)
        await vm.load()

        #expect(vm.collections.count == 2)
        #expect(vm.selectedIds.contains(containedId))
        #expect(vm.selectedIds.contains(otherId) == false)
        MockURLProtocol.requestHandler = nil
    }
}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionPickerViewModelTests
```
Expected: FAILS to compile — `CollectionPickerViewModel` does not exist yet.

- [ ] **Step 3: Write `CollectionPickerViewModel`**

```swift
import Foundation
import Combine

@MainActor
class CollectionPickerViewModel: ObservableObject {
    @Published var collections: [CollectionResponse] = []
    @Published var selectedIds: Set<String> = []
    @Published var isLoading = false
    @Published var loadError: String?

    let userTranscriptId: UUID

    init(userTranscriptId: UUID) {
        self.userTranscriptId = userTranscriptId
    }

    func load() async {
        isLoading = true
        loadError = nil
        let result: Result<[CollectionResponse], NetworkError> = await withCheckedContinuation { cont in
            CollectionService.listForTranscript(userTranscriptId: userTranscriptId) { res in cont.resume(returning: res) }
        }
        switch result {
        case .success(let list):
            collections = list.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            selectedIds = Set(list.filter { $0.contains == true }.map(\.id))
        case .failure:
            loadError = "Couldn't load collections."
        }
        isLoading = false
    }

    func toggle(_ collection: CollectionResponse) async {
        guard let collectionId = UUID(uuidString: collection.id) else { return }
        if selectedIds.contains(collection.id) {
            selectedIds.remove(collection.id)
            let result: Result<Void, NetworkError> = await withCheckedContinuation { cont in
                CollectionService.removeTranscript(collectionId: collectionId, userTranscriptId: userTranscriptId) { res in
                    cont.resume(returning: res)
                }
            }
            if case .failure = result { selectedIds.insert(collection.id) } // revert on failure
        } else {
            selectedIds.insert(collection.id)
            let result: Result<Void, NetworkError> = await withCheckedContinuation { cont in
                CollectionService.addTranscript(collectionId: collectionId, userTranscriptId: userTranscriptId) { res in
                    cont.resume(returning: res)
                }
            }
            if case .failure = result { selectedIds.remove(collection.id) } // revert on failure
        }
    }
}
```

- [ ] **Step 4: Write `CollectionPickerSheet`**

```swift
import SwiftUI

struct CollectionPickerSheet: View {
    let userTranscriptId: UUID

    @StateObject private var viewModel: CollectionPickerViewModel
    @Environment(\.dismiss) private var dismiss

    init(userTranscriptId: UUID) {
        self.userTranscriptId = userTranscriptId
        _viewModel = StateObject(wrappedValue: CollectionPickerViewModel(userTranscriptId: userTranscriptId))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Add to collection")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(AppColors.primaryText)
                Spacer()
                Button("Done") { dismiss() }.tint(AppColors.scoopPurple)
            }
            .padding(20)
            Divider().padding(.horizontal, 20)

            if viewModel.isLoading && viewModel.collections.isEmpty {
                ProgressView().tint(AppColors.scoopPurple)
                    .frame(maxWidth: .infinity).padding(.vertical, 40)
            } else if let error = viewModel.loadError {
                Text(error).foregroundColor(AppColors.secondaryText).padding(20)
            } else {
                List {
                    ForEach(viewModel.collections) { collection in
                        Button {
                            Task { await viewModel.toggle(collection) }
                        } label: {
                            HStack {
                                Text(collection.name).foregroundColor(AppColors.primaryText)
                                Spacer()
                                if viewModel.selectedIds.contains(collection.id) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(AppColors.scoopPurple)
                                } else {
                                    Image(systemName: "circle")
                                        .foregroundColor(AppColors.secondaryText.opacity(0.7))
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
            Spacer()
        }
        .presentationDetents([.medium, .large])
        .task { await viewModel.load() }
    }
}
```

- [ ] **Step 5: Add a "Collections" entry to `DashboardScreen`**

Add this `NavigationLink` next to the existing category browse navigation (near the `NavigationLink(destination: AddLinkScreen())` block around line 82), so collections become a parallel browse axis (§10):

```swift
NavigationLink(destination: CollectionsScreen()) {
    HStack(spacing: 12) {
        Image(systemName: "rectangle.stack")
            .foregroundColor(AppColors.scoopPurple)
        Text("Collections")
            .font(.system(size: 17, weight: .semibold))
            .foregroundColor(AppColors.primaryText)
        Spacer()
        Image(systemName: "chevron.right")
            .foregroundColor(AppColors.secondaryText)
    }
    .padding(.horizontal, 20)
    .padding(.vertical, 14)
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run:
```bash
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:ScoopTests/CollectionPickerViewModelTests
```
Expected: TEST SUCCEEDED.

- [ ] **Step 7: Commit**

```bash
git add Scoop/viewModel/CollectionPickerViewModel.swift Scoop/views/Feed/CollectionPickerSheet.swift Scoop/views/Dashboard/DashboardScreen.swift ScoopTests/CollectionPickerViewModelTests.swift
git commit -m "feat: add collection picker sheet and Dashboard entry"
```

---

## Self-Review

### Spec coverage (each spec section → task)

| Spec section | Requirement | Task(s) |
|---|---|---|
| §4.1 | Explicit join entity `CollectionTranscriptEntity` (not `@ManyToMany`) | Task 3 |
| §4.2 | Per-user rows; double ownership check on add | Task 10 (`addTranscript`), Task 11 |
| §4.3 | Both join FKs `ON DELETE CASCADE`; collection is source of truth | Task 1 (migration), Task 5 (cascade test) |
| §4.4 | `/api/v1/collections` endpoint surface (incl. `?transcriptId=` + `/order`) | Task 12 (controller), Task 13 (tests) |
| §4.5 | Picker pre-check via transient `contains` on the list-collections variant only | Task 8 (`CollectionDto.contains`), Task 9 (mapper), Task 10 (`collectionIdsContaining`), Task 12, Task 13 (`contains` variant), Task 18 (picker) |
| §4.6 | `position` column; append on add (`COALESCE(MAX,-1)+1`); read `position ASC, added_at ASC`; partial reorder from min position | Task 1, Task 4 (`findMaxPosition`/`findMembers`), Task 10 (`addTranscript`/`reorderTranscripts`), Task 11, Task 17 (drag-to-reorder) |
| §4.7 | Soft caps 200/user (422) + 5000/collection (422) | Task 7 (exception), Task 10 (`MAX_*` checks), Task 11, Task 13 (422 cases) |
| §5 | Layered architecture mirroring UserSubcategory slice | Tasks 2–13 |
| §6 | Migration `V28__add_collections.sql` | Task 1 |
| §7.1 | `CollectionEntity` (UUID id, timestamps, unique `(user_id,name)`) | Task 2 |
| §7.2 | `CollectionTranscriptEntity` composite key + `@ManyToOne`s + `added_at` | Task 3 |
| §7.3 | `Collection` pure domain model | Task 6 |
| §7.4 | Repositories (ownership finders, counts projection, members page) | Task 4, Task 5 |
| §7.5 | `CollectionService` CRUD + add/remove + reorder + limits | Task 10, Task 11 |
| §7.6 | `CollectionMapper` `@Component` with count + optional `contains` | Task 9 |
| §7.7 | Record DTOs (`CollectionDto`, `CreateCollectionRequest`, `UpdateCollectionRequest`) | Task 8; `ReorderCollectionRequest` (Task 8), `AddCollectionTranscriptRequest` (Task 12) |
| §7.8 | `CollectionController` w/ `UserPrincipal` + `requireUser` | Task 12 |
| §8 | Member listing reuses `TranscriptPageResponse` via `videoMapper.buildResponse` | Task 10 (`listTranscripts`), Task 12 |
| §9 | Edge cases: 409 dup name, idempotent add 200, 404 ownership, 204 remove no-op, 422 caps, 400 unknown reorder id, 204 empty reorder | Task 10, Task 11, Task 13 |
| §10 | iOS: `CollectionResponse`, `CollectionService` (+ reorder), picker sheet using `contains`, browse list + detail w/ drag-to-reorder, Dashboard entry | Tasks 14–18 |
| §11 | Tests: service unit, repository/Testcontainers, controller, iOS service/decoding | Task 5, Task 11, Task 13, Task 14, Task 15 (+ VM tests Tasks 16–18) |
| §12 | Resolved decisions (principal style, ordering, contains flag, member sort, limits) | Reflected across Tasks 10–18 |

### Placeholder scan

`grep -nE "TBD|TODO|FIXME|placeholder|add error handling|XXX"` over the plan returns **no matches**. Every task contains complete, runnable code (no stubs), exact file paths, exact run commands, and explicit expected pass/fail outcomes.

### Type-consistency check (names match across Tasks 1–18)

- **Entities:** `CollectionEntity` (Task 2), `CollectionTranscriptId` + `CollectionTranscriptEntity` (Task 3) — referenced identically in repositories (Task 4), repo test (Task 5), service (Task 10) and service test (Task 11). `CollectionTranscriptEntity(collection, userTranscript, position)` 3-arg constructor (Task 3) is the one called in Tasks 5 & 10.
- **Repositories:** `CollectionRepository` (`countByUserId`, `findByUserId`, `findByIdAndUserId`, `findByUserIdAndNameIgnoreCase`), `CollectionTranscriptRepository` (`existsByCollection_IdAndUserTranscript_Id`, `countByCollection_Id`, `deleteByCollection_IdAndUserTranscript_Id`, `findMaxPosition`, `findByCollection_IdAndUserTranscript_IdIn`, `countsByUser`, `findCollectionIdsContaining`, `findMembers`), `CollectionCountProjection.getId()/getCnt()` — every method invoked in Tasks 10/11 is declared in Task 4.
- **Exceptions:** `CollectionNotFoundException` (404) and `CollectionLimitExceededException` (422) (Task 7) — thrown in Task 10 and asserted in Tasks 11 & 13. `TranscriptNotFoundException` is the existing repo type (verified present).
- **DTOs:** `CollectionDto(id, name, description, transcriptCount, createdAt, updatedAt, contains)` (Task 8) — constructed identically by `CollectionMapper.toDto(entity, count)` / `toDto(entity, count, contains)` (Task 9) and in Task 13. `CreateCollectionRequest`/`UpdateCollectionRequest`/`ReorderCollectionRequest` (Task 8) and `AddCollectionTranscriptRequest` (Task 12) match the controller's `request.name()/.description()/.userTranscriptIds()/.userTranscriptId()` accessors.
- **Service API:** `CollectionService` methods (`createCollection`, `listCollections`, `memberCounts`, `collectionIdsContaining`, `updateCollection`, `deleteCollection`, `addTranscript`, `removeTranscript`, `listTranscripts`, `reorderTranscripts`, `getOwnedCollection`) called by the controller (Task 12) and mocked in Task 13 with matching signatures.
- **Controller ↔ existing code:** `@RequestMapping("/api/v1")`, `@AuthenticationPrincipal UserPrincipal`, private `requireUser(...)`, and `TranscriptPageResponse` page-clamping (`Math.max(page,0)`, `Math.min(Math.max(size,1),100)`) match `CategoryController`/`TranscriptController`.
- **iOS:** `CollectionResponse` fields (Task 14) consumed by `CollectionService` (Task 15) and all three view models (Tasks 16–18). `CollectionService` method names (`getCollections`, `listForTranscript`, `createCollection`, `renameCollection`, `deleteCollection`, `addTranscript`, `removeTranscript`, `getCollectionTranscripts`, `reorderTranscripts`) are called consistently by Tasks 16–18. `TranscriptPageResponse` (Task 15) mirrors the backend record field-for-field (`items/page/size/totalItems/totalPages/hasNext`). `[TranscriptResponse].toDomain()` (existing `TranscriptMapper`) feeds `TranscriptCard` (Task 17). Endpoint paths/bodies in iOS exactly match the backend routes/DTO field names from Tasks 8 & 12.

### Known cross-repo notes (called out, not placeholders)

- iOS view models are placed in `Scoop/viewModel/` (the real repo location), not the `views/Collections/` path the original File Structure header implied — this matches the existing `FeedViewModel`/`DashboardViewModel` convention.
- `HTTPError.conflict` (duplicate-name 409) is mapped to `NetworkError.requestFailed` via the existing services' `default:` branch; surfacing a friendlier dedicated message is a UI nicety left to the picker/list copy and is not required by the spec's `NetworkError` enum (which has only `invalidURL`/`requestFailed`/`decodingError`).
