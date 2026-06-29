# Personal Analytics Dashboard Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a read-only personal analytics dashboard ("Your Insights") that surfaces per-user totals, content-saved duration, saved-per-period series, category/platform/creator breakdowns, and activity streaks via a single composite backend endpoint and a SwiftUI Insights screen.

**Architecture:** Compute-on-read (no stats table), mirroring the Subscription/Usage slice. A new `AnalyticsController` (`GET /api/v1/analytics?tz=&from=&to=`) delegates to `AnalyticsService` → `AnalyticsServiceImpl`, which calls aggregate repository queries on `user_transcripts` joined to `base_transcripts` and computes streaks in-service in the client's IANA timezone. iOS adds an `AnalyticsResponse`/`Analytics`/`AnalyticsService`/`AnalyticsViewModel`/`InsightsScreen` slice mirroring the Subscription/Usage pattern, charts via Swift Charts, and an Insights row in `ProfileScreen`.

**Tech Stack:** Backend — Java 24, Spring Boot 3.5, Maven, PostgreSQL + Flyway, JUnit 5 + Mockito + AssertJ + Testcontainers. iOS — SwiftUI app "Scoop", iOS 18.x, Swift Charts, Swift Testing (`import Testing`) + `MockURLProtocol`.

**Stack position:** This is stack position 2, rebased on the Collections PR which adds migration **V28**. Analytics Phase 1 needs **NO migration**. The OPTIONAL composite index `(user_id, created_at)` is **V29** and only added if query plans show seq scans (see Task 11).

**Backend package root:** `com.app.categorise` under `src/main/java/com/app/categorise/`.
**Backend test root:** `src/test/java/com/app/categorise/`.
**iOS repo root:** `/Users/apushpavannan/personal/TranscribeAssistant-ios` (Xcode project `Scoop.xcodeproj`, test target `ScoopTests`).

---

## File Structure

**Backend (content-categorise):**
- Create: `src/main/java/com/app/categorise/api/dto/analytics/AnalyticsDto.java` — composite response record + nested records/enum.
- Create: `src/main/java/com/app/categorise/data/repository/AnalyticsProjections.java` — projection interfaces for the JPQL aggregate queries.
- Modify: `src/main/java/com/app/categorise/data/repository/UserTranscriptRepository.java` — add JPQL aggregate queries (count, sum duration, by-category, by-platform, by-creator).
- Modify: `src/main/java/com/app/categorise/data/repository/CustomUserTranscriptRepository.java` — add native-query method signatures (saved-per-period, distinct save-days).
- Modify: `src/main/java/com/app/categorise/data/repository/UserTranscriptRepositoryImpl.java` — implement the two native queries with `date_trunc(... AT TIME ZONE :tz)`.
- Create: `src/main/java/com/app/categorise/domain/service/AnalyticsService.java` — domain interface.
- Create: `src/main/java/com/app/categorise/application/internal/AnalyticsServiceImpl.java` — `@Service`, range resolution, granularity selection, zero-fill, streak walk, DTO assembly.
- Create: `src/main/java/com/app/categorise/api/controller/AnalyticsController.java` — `GET /api/v1/analytics`.
- Create (OPTIONAL, Task 11): `src/main/resources/db/migration/V29__add_user_transcripts_user_created_at_index.sql`.

**Backend tests:**
- Create: `src/test/java/com/app/categorise/application/internal/AnalyticsServiceImplTest.java` — pure-logic unit tests (Mockito + AssertJ), nested by method-under-test, one layer deep.
- Modify: `src/test/java/com/app/categorise/data/repository/UserTranscriptRepositoryTest.java` — Testcontainers tests for the new aggregate + native queries.
- Create: `src/test/java/com/app/categorise/api/controller/AnalyticsControllerTest.java` — `@WebMvcTest` controller tests.

**iOS (Scoop):**
- Create: `Scoop/models/api/AnalyticsResponse.swift` — `Decodable` mirroring `AnalyticsDto`.
- Create: `Scoop/models/domain/Analytics.swift` — domain struct(s) + `toAnalytics` mapper.
- Create: `Scoop/service/AnalyticsService.swift` — `getAnalytics(tz:from:to:)`.
- Create: `Scoop/viewModel/AnalyticsViewModel.swift` — `@MainActor`, loading/loaded/empty/error states.
- Create: `Scoop/views/Insights/InsightsScreen.swift` — Swift Charts dashboard + states.
- Modify: `Scoop/views/Profile/ProfileScreen.swift` — add Insights `ProfileActionRow` + `.sheet`.

**iOS tests:**
- Create: `ScoopTests/AnalyticsResponseTests.swift` — decoding (incl. nulls/empties).
- Create: `ScoopTests/AnalyticsServiceTests.swift` — request path/query + mapping via `MockURLProtocol`.
- Create: `ScoopTests/AnalyticsViewModelTests.swift` — state transitions.

---

## Chunk 1: Backend data layer — DTO, projections, repository queries

### Task 1: AnalyticsDto composite record

**Files:**
- Create: `src/main/java/com/app/categorise/api/dto/analytics/AnalyticsDto.java`

- [ ] **Step 1: Write the failing test**

Add this test class. It compiles only once the DTO exists.

Create `src/test/java/com/app/categorise/api/dto/analytics/AnalyticsDtoTest.java`:

```java
package com.app.categorise.api.dto.analytics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsDtoTest {

    @Test
    void emptyAnalytics_hasZeroTotalsAndEmptyLists() {
        AnalyticsDto dto = new AnalyticsDto(
                0L, 0.0, 0, 0,
                Instant.parse("2025-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T00:00:00Z"),
                "UTC",
                AnalyticsDto.PeriodGranularity.MONTH,
                List.of(), List.of(), List.of(), List.of());

        assertThat(dto.totalTranscripts()).isZero();
        assertThat(dto.totalDurationSeconds()).isZero();
        assertThat(dto.currentStreakDays()).isZero();
        assertThat(dto.longestStreakDays()).isZero();
        assertThat(dto.timezone()).isEqualTo("UTC");
        assertThat(dto.granularity()).isEqualTo(AnalyticsDto.PeriodGranularity.MONTH);
        assertThat(dto.savedPerPeriod()).isEmpty();
        assertThat(dto.byCategory()).isEmpty();
        assertThat(dto.byPlatform()).isEmpty();
        assertThat(dto.topCreators()).isEmpty();
    }

    @Test
    void nestedRecords_exposeTheirFields() {
        UUID categoryId = UUID.randomUUID();
        Instant periodStart = Instant.parse("2026-01-01T00:00:00Z");

        var period = new AnalyticsDto.SavedPerPeriod(periodStart, 5L);
        var category = new AnalyticsDto.CategoryCount(categoryId, "Technology", 7L);
        var platform = new AnalyticsDto.PlatformCount("youtube", 9L);
        var creator = new AnalyticsDto.CreatorCount("Some Channel", "acct-123", 4L);

        assertThat(period.periodStart()).isEqualTo(periodStart);
        assertThat(period.count()).isEqualTo(5L);
        assertThat(category.categoryId()).isEqualTo(categoryId);
        assertThat(category.name()).isEqualTo("Technology");
        assertThat(category.count()).isEqualTo(7L);
        assertThat(platform.platform()).isEqualTo("youtube");
        assertThat(platform.count()).isEqualTo(9L);
        assertThat(creator.account()).isEqualTo("Some Channel");
        assertThat(creator.accountId()).isEqualTo("acct-123");
        assertThat(creator.count()).isEqualTo(4L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=AnalyticsDtoTest`
Expected: COMPILE FAILURE / FAIL — `AnalyticsDto` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/app/categorise/api/dto/analytics/AnalyticsDto.java`:

```java
package com.app.categorise.api.dto.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Composite read-only analytics payload for a single user.
 *
 * <p>{@code totalDurationSeconds} is the "Content saved" metric — the factual total
 * source-video length in the user's library for the selected range. It is intentionally
 * NOT framed as "time saved".</p>
 *
 * <p>{@code timezone} echoes the IANA zone actually used for all day-bucketing (the value
 * of {@code ?tz=}, or {@code "UTC"} when the supplied zone was missing/invalid).</p>
 */
public record AnalyticsDto(
        long totalTranscripts,
        double totalDurationSeconds,
        int currentStreakDays,
        int longestStreakDays,
        Instant rangeFrom,
        Instant rangeTo,
        String timezone,
        PeriodGranularity granularity,
        List<SavedPerPeriod> savedPerPeriod,
        List<CategoryCount> byCategory,
        List<PlatformCount> byPlatform,
        List<CreatorCount> topCreators
) {
    /** One bucket of the saved-per-period time series. */
    public record SavedPerPeriod(Instant periodStart, long count) {}

    /** Saves grouped by category; {@code categoryId} is null for the "Uncategorised" bucket. */
    public record CategoryCount(UUID categoryId, String name, long count) {}

    /** Saves grouped by platform; unknown platforms map to a sentinel label. */
    public record PlatformCount(String platform, long count) {}

    /** Saves grouped by creator; grouped by accountId when present, displayed via account. */
    public record CreatorCount(String account, String accountId, long count) {}

    /** Granularity of the saved-per-period series. */
    public enum PeriodGranularity { WEEK, MONTH }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=AnalyticsDtoTest`
Expected: PASS (2 tests green).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/api/dto/analytics/AnalyticsDto.java \
        src/test/java/com/app/categorise/api/dto/analytics/AnalyticsDtoTest.java
git commit -m "feat(analytics): add AnalyticsDto composite response record"
```

---

### Task 2: Projection interfaces for aggregate queries

**Files:**
- Create: `src/main/java/com/app/categorise/data/repository/AnalyticsProjections.java`

Spring Data projections let JPQL `GROUP BY` results map directly onto typed accessors without a `new ...()` constructor expression. We use interface projections (Spring binds by getter name to the JPQL aliases).

- [ ] **Step 1: Write the failing test**

Add `src/test/java/com/app/categorise/data/repository/AnalyticsProjectionsTest.java`:

```java
package com.app.categorise.data.repository;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsProjectionsTest {

    @Test
    void categoryCount_recordImplementsProjection() {
        UUID id = UUID.randomUUID();
        AnalyticsProjections.CategoryCount c =
                new AnalyticsProjections.CategoryCountValue(id, "Tech", 3L);
        assertThat(c.getCategoryId()).isEqualTo(id);
        assertThat(c.getName()).isEqualTo("Tech");
        assertThat(c.getCount()).isEqualTo(3L);
    }

    @Test
    void platformCount_recordImplementsProjection() {
        AnalyticsProjections.PlatformCount p =
                new AnalyticsProjections.PlatformCountValue("youtube", 5L);
        assertThat(p.getPlatform()).isEqualTo("youtube");
        assertThat(p.getCount()).isEqualTo(5L);
    }

    @Test
    void creatorCount_recordImplementsProjection() {
        AnalyticsProjections.CreatorCount cr =
                new AnalyticsProjections.CreatorCountValue("Chan", "acct-1", 7L);
        assertThat(cr.getAccount()).isEqualTo("Chan");
        assertThat(cr.getAccountId()).isEqualTo("acct-1");
        assertThat(cr.getCount()).isEqualTo(7L);
    }

    @Test
    void periodCount_recordImplementsProjection() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        AnalyticsProjections.PeriodCount pc =
                new AnalyticsProjections.PeriodCountValue(start, 9L);
        assertThat(pc.getPeriodStart()).isEqualTo(start);
        assertThat(pc.getCount()).isEqualTo(9L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=AnalyticsProjectionsTest`
Expected: COMPILE FAILURE — `AnalyticsProjections` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/app/categorise/data/repository/AnalyticsProjections.java`:

```java
package com.app.categorise.data.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface projections (plus concrete record values for the
 * native-query and service layers) used by the analytics aggregate queries.
 *
 * <p>Interface projections bind by getter name to JPQL aliases, so the JPQL must
 * alias columns to match (e.g. {@code AS categoryId}). The {@code *Value} records
 * are returned by the native queries in {@link UserTranscriptRepositoryImpl} and
 * are convenient immutable carriers in tests.</p>
 */
public final class AnalyticsProjections {

    private AnalyticsProjections() {}

    public interface CategoryCount {
        UUID getCategoryId();
        String getName();
        long getCount();
    }

    public interface PlatformCount {
        String getPlatform();
        long getCount();
    }

    public interface CreatorCount {
        String getAccount();
        String getAccountId();
        long getCount();
    }

    public interface PeriodCount {
        Instant getPeriodStart();
        long getCount();
    }

    public record CategoryCountValue(UUID categoryId, String name, long count)
            implements CategoryCount {
        @Override public UUID getCategoryId() { return categoryId; }
        @Override public String getName() { return name; }
        @Override public long getCount() { return count; }
    }

    public record PlatformCountValue(String platform, long count)
            implements PlatformCount {
        @Override public String getPlatform() { return platform; }
        @Override public long getCount() { return count; }
    }

    public record CreatorCountValue(String account, String accountId, long count)
            implements CreatorCount {
        @Override public String getAccount() { return account; }
        @Override public String getAccountId() { return accountId; }
        @Override public long getCount() { return count; }
    }

    public record PeriodCountValue(Instant periodStart, long count)
            implements PeriodCount {
        @Override public Instant getPeriodStart() { return periodStart; }
        @Override public long getCount() { return count; }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=AnalyticsProjectionsTest`
Expected: PASS (4 tests green).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/data/repository/AnalyticsProjections.java \
        src/test/java/com/app/categorise/data/repository/AnalyticsProjectionsTest.java
git commit -m "feat(analytics): add projection types for aggregate queries"
```

---

### Task 3: JPQL aggregate queries on UserTranscriptRepository

**Files:**
- Modify: `src/main/java/com/app/categorise/data/repository/UserTranscriptRepository.java` (append new methods before the closing brace at line 84)
- Test: `src/test/java/com/app/categorise/data/repository/UserTranscriptRepositoryTest.java` (append new tests)

These mirror the existing `@Query` JPQL style. `COUNT` totals do NOT apply the duration filter (a save with null/<=0 duration still counts); only `sumDuration` applies the `duration > 0` guard. Category grouping uses `LEFT JOIN`-style navigation via `ut.category` so null categories survive as a `categoryId = null` row. Creator grouping groups by `bt.accountId, bt.account` (decision §5.5/§11.5).

- [ ] **Step 1: Write the failing tests**

Append these tests inside `UserTranscriptRepositoryTest` (before the final closing brace). They reuse the existing `user1`, `user2`, `baseTranscript1/2`, `category1/2` fixtures from `setUp()`. Add a helper to persist user_transcripts at a chosen `createdAt`:

```java
    // ---- analytics aggregate helpers + tests ----

    private UserTranscriptEntity saveUt(UserEntity user,
                                        BaseTranscriptEntity bt,
                                        CategoryEntity category,
                                        Instant createdAt) {
        UserTranscriptEntity ut = new UserTranscriptEntity(user.getId(), bt, category);
        ut.setCreatedAt(createdAt);
        return entityManager.persistAndFlush(ut);
    }

    @Test
    void countSavesInRange_countsOnlyUsersRowsInRange() {
        Instant base = Instant.parse("2026-03-01T00:00:00Z");
        saveUt(user1, baseTranscript1, category1, base);
        saveUt(user1, baseTranscript2, category2, base.plusSeconds(86_400));
        saveUt(user2, baseTranscript1, category1, base); // other user, excluded

        long count = userTranscriptRepository.countSavesInRange(
                user1.getId(),
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void sumDurationInRange_excludesNullAndNonPositiveDurations() {
        BaseTranscriptEntity zeroDuration = baseTranscriptRepository.save(new BaseTranscriptEntity(
                "https://example.com/zero", "t", null, "d", "Zero", 0.0,
                Instant.now(), "a", "an", "i", "in"));
        BaseTranscriptEntity nullDuration = baseTranscriptRepository.save(new BaseTranscriptEntity(
                "https://example.com/null", "t", null, "d", "Null", null,
                Instant.now(), "a", "an", "i", "in"));
        Instant base = Instant.parse("2026-03-01T00:00:00Z");
        saveUt(user1, baseTranscript1, category1, base); // 120.0
        saveUt(user1, baseTranscript2, category2, base); // 180.0
        saveUt(user1, zeroDuration, category1, base);    // excluded
        saveUt(user1, nullDuration, category1, base);    // excluded

        double sum = userTranscriptRepository.sumDurationInRange(
                user1.getId(),
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));

        assertThat(sum).isEqualTo(300.0);
    }

    @Test
    void sumDurationInRange_returnsZeroWhenNoRows() {
        double sum = userTranscriptRepository.sumDurationInRange(
                user1.getId(),
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));
        assertThat(sum).isZero();
    }

    @Test
    void countByCategoryInRange_groupsAndKeepsNullCategory() {
        Instant base = Instant.parse("2026-03-01T00:00:00Z");
        saveUt(user1, baseTranscript1, category1, base);
        saveUt(user1, baseTranscript2, category1, base.plusSeconds(60));
        saveUt(user1, baseTranscript1, null, base.plusSeconds(120));

        var rows = userTranscriptRepository.countByCategoryInRange(
                user1.getId(),
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));

        assertThat(rows).hasSize(2);
        // ordered by count desc -> category1 (2) first
        assertThat(rows.get(0).getCategoryId()).isEqualTo(category1.getId());
        assertThat(rows.get(0).getName()).isEqualTo("Technology");
        assertThat(rows.get(0).getCount()).isEqualTo(2);
        assertThat(rows.get(1).getCategoryId()).isNull();
        assertThat(rows.get(1).getCount()).isEqualTo(1);
    }

    @Test
    void countByPlatformInRange_groupsByPlatform() {
        BaseTranscriptEntity yt = baseTranscriptRepository.save(new BaseTranscriptEntity(
                "https://example.com/yt", "t", null, "d", "YT", 10.0,
                Instant.now(), "a", "an", "i", "in"));
        yt.setPlatform("youtube");
        yt = baseTranscriptRepository.save(yt);
        Instant base = Instant.parse("2026-03-01T00:00:00Z");
        saveUt(user1, yt, category1, base);
        saveUt(user1, baseTranscript1, category1, base); // platform null

        var rows = userTranscriptRepository.countByPlatformInRange(
                user1.getId(),
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getPlatform()).isEqualTo("youtube");
            assertThat(r.getCount()).isEqualTo(1);
        });
    }

    @Test
    void countByCreatorInRange_groupsByAccountIdAndAccount() {
        Instant base = Instant.parse("2026-03-01T00:00:00Z");
        // baseTranscript1.account="account_name1", accountId="account1"
        saveUt(user1, baseTranscript1, category1, base);
        saveUt(user1, baseTranscript1, category1, base.plusSeconds(60));
        saveUt(user1, baseTranscript2, category2, base); // different creator

        var rows = userTranscriptRepository.countByCreatorInRange(
                user1.getId(),
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getCount()).isEqualTo(2); // ordered desc
        assertThat(rows.get(0).getAccount()).isEqualTo("account_name1");
        assertThat(rows.get(0).getAccountId()).isEqualTo("account1");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=UserTranscriptRepositoryTest`
Expected: COMPILE FAILURE — the new repository methods do not exist yet.

- [ ] **Step 3: Write minimal implementation**

In `UserTranscriptRepository.java`, add these imports if missing (`import com.app.categorise.data.repository.AnalyticsProjections;` and `import java.time.Instant;`) and insert these methods just before the closing brace (after `findAllByIdInAndUserId`, ~line 84):

```java
    /**
     * Count saves for a user within [from, to]. No duration filter: a save with a
     * missing/<=0 duration still counts as saved.
     */
    @Query("SELECT COUNT(ut) FROM UserTranscriptEntity ut " +
           "WHERE ut.userId = :userId AND ut.createdAt BETWEEN :from AND :to")
    long countSavesInRange(@Param("userId") UUID userId,
                           @Param("from") Instant from,
                           @Param("to") Instant to);

    /**
     * "Content saved" total source-video seconds in range, excluding null/<=0
     * durations. COALESCE avoids null when no rows match.
     */
    @Query("SELECT COALESCE(SUM(bt.duration), 0) FROM UserTranscriptEntity ut " +
           "JOIN ut.baseTranscript bt " +
           "WHERE ut.userId = :userId AND ut.createdAt BETWEEN :from AND :to " +
           "AND bt.duration IS NOT NULL AND bt.duration > 0")
    double sumDurationInRange(@Param("userId") UUID userId,
                             @Param("from") Instant from,
                             @Param("to") Instant to);

    /**
     * Count saves grouped by category in range. Null categories survive as a row
     * with categoryId = null (LEFT JOIN navigation). Ordered by count desc.
     */
    @Query("SELECT ut.category.id AS categoryId, ut.category.name AS name, COUNT(ut) AS count " +
           "FROM UserTranscriptEntity ut " +
           "WHERE ut.userId = :userId AND ut.createdAt BETWEEN :from AND :to " +
           "GROUP BY ut.category.id, ut.category.name " +
           "ORDER BY COUNT(ut) DESC")
    List<AnalyticsProjections.CategoryCount> countByCategoryInRange(@Param("userId") UUID userId,
                                                                    @Param("from") Instant from,
                                                                    @Param("to") Instant to);

    /**
     * Count saves grouped by platform in range. Ordered by count desc. Null/empty
     * platform values survive as a row with platform = null (mapped to a sentinel
     * label in the service).
     */
    @Query("SELECT bt.platform AS platform, COUNT(ut) AS count " +
           "FROM UserTranscriptEntity ut JOIN ut.baseTranscript bt " +
           "WHERE ut.userId = :userId AND ut.createdAt BETWEEN :from AND :to " +
           "GROUP BY bt.platform " +
           "ORDER BY COUNT(ut) DESC")
    List<AnalyticsProjections.PlatformCount> countByPlatformInRange(@Param("userId") UUID userId,
                                                                    @Param("from") Instant from,
                                                                    @Param("to") Instant to);

    /**
     * Count saves grouped by creator in range, grouped by accountId + account
     * (decision §5.5/§11.5). Ordered by count desc; the service truncates to top N.
     */
    @Query("SELECT bt.account AS account, bt.accountId AS accountId, COUNT(ut) AS count " +
           "FROM UserTranscriptEntity ut JOIN ut.baseTranscript bt " +
           "WHERE ut.userId = :userId AND ut.createdAt BETWEEN :from AND :to " +
           "GROUP BY bt.account, bt.accountId " +
           "ORDER BY COUNT(ut) DESC")
    List<AnalyticsProjections.CreatorCount> countByCreatorInRange(@Param("userId") UUID userId,
                                                                  @Param("from") Instant from,
                                                                  @Param("to") Instant to);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=UserTranscriptRepositoryTest`
Expected: PASS — all existing tests plus the 6 new aggregate tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/data/repository/UserTranscriptRepository.java \
        src/test/java/com/app/categorise/data/repository/UserTranscriptRepositoryTest.java
git commit -m "feat(analytics): add JPQL aggregate queries (count, sum, by category/platform/creator)"
```

---

### Task 4: Native timezone-bucketed queries (saved-per-period + distinct save-days)

**Files:**
- Modify: `src/main/java/com/app/categorise/data/repository/CustomUserTranscriptRepository.java` (add 2 method signatures)
- Modify: `src/main/java/com/app/categorise/data/repository/UserTranscriptRepositoryImpl.java` (implement them, mirroring the existing `createNativeQuery` style)
- Test: `src/test/java/com/app/categorise/data/repository/UserTranscriptRepositoryTest.java` (append)

`date_trunc(:granularity, ut.created_at AT TIME ZONE :tz)` truncates in the client zone so "today"/period boundaries match the device. `:tz` is a validated IANA string passed by the service. The distinct-save-days query ignores the dashboard range (streaks are all-time per §5.5) and returns one `LocalDate` per save-day, already truncated in `:tz`.

- [ ] **Step 1: Write the failing tests**

Append to `UserTranscriptRepositoryTest`:

```java
    @Test
    void savedPerPeriod_bucketsByWeekInRequestedZone() {
        // Two saves in the same ISO week, one in the next week (UTC)
        Instant w1a = Instant.parse("2026-03-02T10:00:00Z"); // Mon
        Instant w1b = Instant.parse("2026-03-04T10:00:00Z"); // Wed (same week)
        Instant w2 = Instant.parse("2026-03-10T10:00:00Z");  // next week
        saveUt(user1, baseTranscript1, category1, w1a);
        saveUt(user1, baseTranscript2, category2, w1b);
        saveUt(user1, baseTranscript1, category1, w2);

        var rows = userTranscriptRepository.savedPerPeriod(
                user1.getId(), "week", "UTC",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getCount()).isEqualTo(2); // first week bucket
        assertThat(rows.get(1).getCount()).isEqualTo(1);
        assertThat(rows.get(0).getPeriodStart()).isBefore(rows.get(1).getPeriodStart());
    }

    @Test
    void savedPerPeriod_bucketsByMonthInRequestedZone() {
        saveUt(user1, baseTranscript1, category1, Instant.parse("2026-01-15T10:00:00Z"));
        saveUt(user1, baseTranscript2, category2, Instant.parse("2026-02-20T10:00:00Z"));
        saveUt(user1, baseTranscript1, category1, Instant.parse("2026-02-25T10:00:00Z"));

        var rows = userTranscriptRepository.savedPerPeriod(
                user1.getId(), "month", "UTC",
                Instant.parse("2025-12-01T00:00:00Z"),
                Instant.parse("2026-03-01T00:00:00Z"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getCount()).isEqualTo(1); // Jan
        assertThat(rows.get(1).getCount()).isEqualTo(2); // Feb
    }

    @Test
    void savedPerPeriod_timezoneShiftsDayAcrossBoundary() {
        // 2026-03-02T02:00Z is still 2026-03-01 (21:00) in America/New_York.
        saveUt(user1, baseTranscript1, category1, Instant.parse("2026-03-02T02:00:00Z"));

        var utcRows = userTranscriptRepository.savedPerPeriod(
                user1.getId(), "month", "UTC",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));
        var nyRows = userTranscriptRepository.savedPerPeriod(
                user1.getId(), "month", "America/New_York",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));

        // Both land in March's monthly bucket, but bucket boundaries differ by zone.
        assertThat(utcRows).hasSize(1);
        assertThat(nyRows).hasSize(1);
        assertThat(utcRows.get(0).getCount()).isEqualTo(1);
        assertThat(nyRows.get(0).getCount()).isEqualTo(1);
    }

    @Test
    void distinctSaveDays_returnsAllTimeDaysInZoneAscending() {
        // Range-independent: passes no from/to. Three saves on two distinct days.
        saveUt(user1, baseTranscript1, category1, Instant.parse("2026-03-02T08:00:00Z"));
        saveUt(user1, baseTranscript2, category2, Instant.parse("2026-03-02T20:00:00Z"));
        saveUt(user1, baseTranscript1, category1, Instant.parse("2026-03-05T08:00:00Z"));

        List<java.time.LocalDate> days =
                userTranscriptRepository.distinctSaveDays(user1.getId(), "UTC");

        assertThat(days).containsExactly(
                java.time.LocalDate.parse("2026-03-02"),
                java.time.LocalDate.parse("2026-03-05"));
    }

    @Test
    void distinctSaveDays_emptyForUserWithNoSaves() {
        List<java.time.LocalDate> days =
                userTranscriptRepository.distinctSaveDays(user2.getId(), "UTC");
        assertThat(days).isEmpty();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=UserTranscriptRepositoryTest`
Expected: COMPILE FAILURE — `savedPerPeriod` / `distinctSaveDays` not defined.

- [ ] **Step 3: Write minimal implementation**

In `CustomUserTranscriptRepository.java` add imports `import java.time.LocalDate;` and `import com.app.categorise.data.repository.AnalyticsProjections;`, then add to the interface:

```java
    /**
     * Saved-per-period series bucketed in the given IANA zone.
     * @param granularity 'week' or 'month' (validated by the caller)
     * @param tz validated IANA zone id (e.g. "UTC", "America/New_York")
     */
    List<AnalyticsProjections.PeriodCount> savedPerPeriod(UUID userId, String granularity,
                                                          String tz, Instant from, Instant to);

    /**
     * All-time distinct save-days for the user, truncated to calendar day in {@code tz},
     * returned ascending. Independent of the dashboard range (streaks are all-time).
     */
    List<LocalDate> distinctSaveDays(UUID userId, String tz);
```

In `UserTranscriptRepositoryImpl.java` add imports `import java.time.LocalDate;`, `import java.sql.Timestamp;`, `import com.app.categorise.data.repository.AnalyticsProjections;`, then add these methods (before `toVectorString`):

```java
    @Override
    @SuppressWarnings("unchecked")
    public List<AnalyticsProjections.PeriodCount> savedPerPeriod(UUID userId, String granularity,
                                                                 String tz, Instant from, Instant to) {
        // granularity is validated to 'week'|'month' by the service; concatenated only
        // because date_trunc's first arg cannot be a bind parameter.
        String unit = "week".equals(granularity) ? "week" : "month";
        String sql = """
                SELECT date_trunc(:unit, ut.created_at AT TIME ZONE :tz) AS period_start,
                       COUNT(*) AS cnt
                FROM user_transcripts ut
                WHERE ut.user_id = CAST(:userId AS uuid)
                  AND ut.created_at BETWEEN :from AND :to
                GROUP BY period_start
                ORDER BY period_start
                """;
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("unit", unit)
                .setParameter("tz", tz)
                .setParameter("userId", userId.toString())
                .setParameter("from", Timestamp.from(from))
                .setParameter("to", Timestamp.from(to))
                .getResultList();

        return rows.stream()
                .map(r -> new AnalyticsProjections.PeriodCountValue(
                        toInstant(r[0]),
                        ((Number) r[1]).longValue()))
                .map(v -> (AnalyticsProjections.PeriodCount) v)
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<LocalDate> distinctSaveDays(UUID userId, String tz) {
        String sql = """
                SELECT DISTINCT date_trunc('day', created_at AT TIME ZONE :tz) AS day
                FROM user_transcripts
                WHERE user_id = CAST(:userId AS uuid)
                ORDER BY day
                """;
        List<Object> rows = entityManager.createNativeQuery(sql)
                .setParameter("tz", tz)
                .setParameter("userId", userId.toString())
                .getResultList();

        return rows.stream()
                .map(UserTranscriptRepositoryImpl::toLocalDate)
                .toList();
    }

    private static Instant toInstant(Object value) {
        // date_trunc(... AT TIME ZONE :tz) returns a timestamp WITHOUT time zone whose
        // wall-clock fields are the truncated period start in :tz. The JDBC driver maps it
        // to a Timestamp/LocalDateTime; we treat those fields as the period-start instant
        // in UTC (period boundaries are already shifted by the AT TIME ZONE bucketing).
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof Instant i) {
            return i;
        }
        throw new IllegalStateException("Unexpected period_start type: " + value.getClass());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        throw new IllegalStateException("Unexpected save-day type: " + value.getClass());
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=UserTranscriptRepositoryTest`
Expected: PASS — all repository tests including the 5 new native-query tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/data/repository/CustomUserTranscriptRepository.java \
        src/main/java/com/app/categorise/data/repository/UserTranscriptRepositoryImpl.java \
        src/test/java/com/app/categorise/data/repository/UserTranscriptRepositoryTest.java
git commit -m "feat(analytics): add timezone-bucketed saved-per-period and distinct-save-days native queries"
```

---

## Chunk 2: Backend service layer — interface, impl, unit tests

### Task 5: AnalyticsService domain interface

**Files:**
- Create: `src/main/java/com/app/categorise/domain/service/AnalyticsService.java`

The interface takes the raw `tz` query string (nullable) so timezone resolution + fallback lives in one place (the impl). The controller passes `principal.getId()`, the raw `tz`, and the optional `from`/`to` straight through. This extends the spec's §6.1 signature with a `String tz` parameter, required by the §5.5 "client timezone from day 1" decision (the service must resolve and echo the zone).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/app/categorise/domain/service/AnalyticsServiceTest.java`:

```java
package com.app.categorise.domain.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsServiceTest {

    @Test
    void interface_declaresGetUserAnalyticsWithTzParameter() throws NoSuchMethodException {
        Method method = AnalyticsService.class.getMethod(
                "getUserAnalytics",
                java.util.UUID.class,
                String.class,
                java.time.Instant.class,
                java.time.Instant.class);

        assertThat(method.getReturnType())
                .isEqualTo(com.app.categorise.api.dto.analytics.AnalyticsDto.class);
        assertThat(AnalyticsService.class.isInterface()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=AnalyticsServiceTest`
Expected: COMPILE FAILURE — `AnalyticsService` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/app/categorise/domain/service/AnalyticsService.java`:

```java
package com.app.categorise.domain.service;

import com.app.categorise.api.dto.analytics.AnalyticsDto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only personal analytics for a single user (compute-on-read).
 *
 * <p>Mirrors the {@code UsageService} domain-interface pattern.</p>
 */
public interface AnalyticsService {

    /**
     * Assemble the composite analytics payload for {@code userId}.
     *
     * @param userId the authenticated user's id
     * @param tz     raw IANA zone id from the {@code ?tz=} query parameter; when null,
     *               blank, or invalid the implementation falls back to UTC (never throws)
     *               and echoes the resolved zone in {@link AnalyticsDto#timezone()}
     * @param from   inclusive range start; when null defaults to {@code to} minus 12 months
     * @param to     inclusive range end; when null defaults to now
     * @return the assembled analytics payload (all-zero/empty for a user with no saves)
     */
    AnalyticsDto getUserAnalytics(UUID userId, String tz, Instant from, Instant to);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=AnalyticsServiceTest`
Expected: PASS (1 test green).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/domain/service/AnalyticsService.java \
        src/test/java/com/app/categorise/domain/service/AnalyticsServiceTest.java
git commit -m "feat(analytics): add AnalyticsService domain interface"
```

---

### Task 6: AnalyticsServiceImpl unit tests (Mockito + AssertJ)

**Files:**
- Create: `src/test/java/com/app/categorise/application/internal/AnalyticsServiceImplTest.java`

Pure-logic unit tests with a mocked `UserTranscriptRepository`. Per AGENTS.md, tests are grouped by the method-under-test (`getUserAnalytics`) nested **one layer deep only** via a single `@Nested` class. These tests are written first and FAIL because `AnalyticsServiceImpl` does not yet exist (Task 7 implements it). Use a fixed clock injected via the constructor so "today"/"yesterday" streak assertions are deterministic.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/app/categorise/application/internal/AnalyticsServiceImplTest.java`:

```java
package com.app.categorise.application.internal;

import com.app.categorise.api.dto.analytics.AnalyticsDto;
import com.app.categorise.data.repository.AnalyticsProjections;
import com.app.categorise.data.repository.UserTranscriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceImplTest {

    // Fixed "now" = 2026-06-28T12:00:00Z so "today" in UTC is 2026-06-28.
    private static final Instant NOW = Instant.parse("2026-06-28T12:00:00Z");

    private UserTranscriptRepository repository;
    private AnalyticsServiceImpl service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        repository = mock(UserTranscriptRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AnalyticsServiceImpl(repository, clock);
        userId = UUID.randomUUID();
    }

    private void stubEmptyAggregates() {
        when(repository.countSavesInRange(any(), any(), any())).thenReturn(0L);
        when(repository.sumDurationInRange(any(), any(), any())).thenReturn(0.0);
        when(repository.countByCategoryInRange(any(), any(), any())).thenReturn(List.of());
        when(repository.countByPlatformInRange(any(), any(), any())).thenReturn(List.of());
        when(repository.countByCreatorInRange(any(), any(), any())).thenReturn(List.of());
        when(repository.savedPerPeriod(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(repository.distinctSaveDays(any(), any())).thenReturn(List.of());
    }

    @Nested
    @DisplayName("getUserAnalytics")
    class GetUserAnalytics {

        @Test
        void invalidTimezone_fallsBackToUtc() {
            stubEmptyAggregates();

            AnalyticsDto dto = service.getUserAnalytics(userId, "Not/AZone", null, null);

            assertThat(dto.timezone()).isEqualTo("UTC");
            // distinctSaveDays must have been called with the resolved UTC zone, never the invalid one.
            ArgumentCaptor<String> tzCaptor = ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(repository).distinctSaveDays(eq(userId), tzCaptor.capture());
            assertThat(tzCaptor.getValue()).isEqualTo("UTC");
        }

        @Test
        void nullTimezone_fallsBackToUtc() {
            stubEmptyAggregates();

            AnalyticsDto dto = service.getUserAnalytics(userId, null, null, null);

            assertThat(dto.timezone()).isEqualTo("UTC");
        }

        @Test
        void validTimezone_isEchoedAndUsed() {
            stubEmptyAggregates();

            AnalyticsDto dto = service.getUserAnalytics(userId, "America/New_York", null, null);

            assertThat(dto.timezone()).isEqualTo("America/New_York");
            org.mockito.Mockito.verify(repository)
                    .distinctSaveDays(eq(userId), eq("America/New_York"));
        }

        @Test
        void noRangeProvided_defaultsToLast12Months() {
            stubEmptyAggregates();

            service.getUserAnalytics(userId, "UTC", null, null);

            ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
            org.mockito.Mockito.verify(repository)
                    .countSavesInRange(eq(userId), fromCaptor.capture(), toCaptor.capture());

            assertThat(toCaptor.getValue()).isEqualTo(NOW);
            assertThat(fromCaptor.getValue())
                    .isEqualTo(NOW.atZone(ZoneOffset.UTC).minusMonths(12).toInstant());
        }

        @Test
        void zeroUser_returnsAllZerosAndEmpties() {
            stubEmptyAggregates();

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.totalTranscripts()).isZero();
            assertThat(dto.totalDurationSeconds()).isZero();
            assertThat(dto.currentStreakDays()).isZero();
            assertThat(dto.longestStreakDays()).isZero();
            assertThat(dto.savedPerPeriod()).isEmpty();
            assertThat(dto.byCategory()).isEmpty();
            assertThat(dto.byPlatform()).isEmpty();
            assertThat(dto.topCreators()).isEmpty();
        }

        @Test
        void totals_areAssembledFromRepository() {
            stubEmptyAggregates();
            when(repository.countSavesInRange(any(), any(), any())).thenReturn(42L);
            when(repository.sumDurationInRange(any(), any(), any())).thenReturn(3600.0);

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.totalTranscripts()).isEqualTo(42L);
            assertThat(dto.totalDurationSeconds()).isEqualTo(3600.0);
        }

        @Test
        void breakdowns_mapProjectionsToNestedRecords() {
            stubEmptyAggregates();
            UUID catId = UUID.randomUUID();
            when(repository.countByCategoryInRange(any(), any(), any())).thenReturn(List.of(
                    new AnalyticsProjections.CategoryCountValue(catId, "Technology", 7L)));
            when(repository.countByPlatformInRange(any(), any(), any())).thenReturn(List.of(
                    new AnalyticsProjections.PlatformCountValue("youtube", 5L)));
            when(repository.countByCreatorInRange(any(), any(), any())).thenReturn(List.of(
                    new AnalyticsProjections.CreatorCountValue("Some Channel", "acct-1", 4L)));

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.byCategory()).containsExactly(
                    new AnalyticsDto.CategoryCount(catId, "Technology", 7L));
            assertThat(dto.byPlatform()).containsExactly(
                    new AnalyticsDto.PlatformCount("youtube", 5L));
            assertThat(dto.topCreators()).containsExactly(
                    new AnalyticsDto.CreatorCount("Some Channel", "acct-1", 4L));
        }

        @Test
        void nullCategory_mapsToUncategorisedBucket() {
            stubEmptyAggregates();
            when(repository.countByCategoryInRange(any(), any(), any())).thenReturn(List.of(
                    new AnalyticsProjections.CategoryCountValue(null, null, 3L)));

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.byCategory()).containsExactly(
                    new AnalyticsDto.CategoryCount(null, "Uncategorised", 3L));
        }

        @Test
        void nullPlatformAndAccount_mapToUnknownSentinel() {
            stubEmptyAggregates();
            when(repository.countByPlatformInRange(any(), any(), any())).thenReturn(List.of(
                    new AnalyticsProjections.PlatformCountValue(null, 2L)));
            when(repository.countByCreatorInRange(any(), any(), any())).thenReturn(List.of(
                    new AnalyticsProjections.CreatorCountValue(null, null, 6L)));

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.byPlatform()).containsExactly(
                    new AnalyticsDto.PlatformCount("Unknown", 2L));
            assertThat(dto.topCreators()).containsExactly(
                    new AnalyticsDto.CreatorCount("Unknown", null, 6L));
        }

        @Test
        void topCreators_truncatedToTen() {
            stubEmptyAggregates();
            List<AnalyticsProjections.CreatorCount> creators = new java.util.ArrayList<>();
            for (int i = 0; i < 15; i++) {
                creators.add(new AnalyticsProjections.CreatorCountValue(
                        "C" + i, "acct-" + i, 15L - i));
            }
            when(repository.countByCreatorInRange(any(), any(), any())).thenReturn(creators);

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.topCreators()).hasSize(10);
            assertThat(dto.topCreators().get(0).account()).isEqualTo("C0");
        }

        @Test
        void granularity_isWeekForShortRanges() {
            stubEmptyAggregates();
            Instant from = NOW.minus(60, ChronoUnit.DAYS);

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", from, NOW);

            assertThat(dto.granularity()).isEqualTo(AnalyticsDto.PeriodGranularity.WEEK);
            org.mockito.Mockito.verify(repository)
                    .savedPerPeriod(eq(userId), eq("week"), eq("UTC"), eq(from), eq(NOW));
        }

        @Test
        void granularity_isMonthForLongRanges() {
            stubEmptyAggregates();

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.granularity()).isEqualTo(AnalyticsDto.PeriodGranularity.MONTH);
            org.mockito.Mockito.verify(repository)
                    .savedPerPeriod(eq(userId), eq("month"), eq("UTC"), any(), any());
        }

        @Test
        void currentStreak_endingToday_countsConsecutiveRun() {
            stubEmptyAggregates();
            // today, yesterday, 2 days ago => current streak 3
            when(repository.distinctSaveDays(any(), any())).thenReturn(List.of(
                    LocalDate.parse("2026-06-26"),
                    LocalDate.parse("2026-06-27"),
                    LocalDate.parse("2026-06-28")));

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.currentStreakDays()).isEqualTo(3);
            assertThat(dto.longestStreakDays()).isEqualTo(3);
        }

        @Test
        void currentStreak_endingYesterday_isStillCurrent() {
            stubEmptyAggregates();
            // yesterday + day before => current streak 2 (run ending yesterday counts)
            when(repository.distinctSaveDays(any(), any())).thenReturn(List.of(
                    LocalDate.parse("2026-06-26"),
                    LocalDate.parse("2026-06-27")));

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.currentStreakDays()).isEqualTo(2);
            assertThat(dto.longestStreakDays()).isEqualTo(2);
        }

        @Test
        void currentStreak_isZeroWhenLastSaveOlderThanYesterday() {
            stubEmptyAggregates();
            when(repository.distinctSaveDays(any(), any())).thenReturn(List.of(
                    LocalDate.parse("2026-06-20"),
                    LocalDate.parse("2026-06-21")));

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.currentStreakDays()).isZero();
            assertThat(dto.longestStreakDays()).isEqualTo(2);
        }

        @Test
        void longestStreak_exceedsCurrentWhenEarlierRunIsLonger() {
            stubEmptyAggregates();
            // earlier run of 4 (Jun 1-4), current run of 2 (Jun 27-28)
            when(repository.distinctSaveDays(any(), any())).thenReturn(List.of(
                    LocalDate.parse("2026-06-01"),
                    LocalDate.parse("2026-06-02"),
                    LocalDate.parse("2026-06-03"),
                    LocalDate.parse("2026-06-04"),
                    LocalDate.parse("2026-06-27"),
                    LocalDate.parse("2026-06-28")));

            AnalyticsDto dto = service.getUserAnalytics(userId, "UTC", null, null);

            assertThat(dto.currentStreakDays()).isEqualTo(2);
            assertThat(dto.longestStreakDays()).isEqualTo(4);
        }

        @Test
        void streak_isComputedInResolvedZone_notUtc() {
            stubEmptyAggregates();
            // A single save-day list in America/New_York where "today" (Jun 28, NY) is reached.
            when(repository.distinctSaveDays(eq(userId), eq("America/New_York"))).thenReturn(List.of(
                    LocalDate.parse("2026-06-27"),
                    LocalDate.parse("2026-06-28")));

            AnalyticsDto dto = service.getUserAnalytics(userId, "America/New_York", null, null);

            // NOW (2026-06-28T12:00Z) is 08:00 in New_York, still Jun 28 -> current streak 2.
            assertThat(dto.currentStreakDays()).isEqualTo(2);
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=AnalyticsServiceImplTest`
Expected: COMPILE FAILURE — `AnalyticsServiceImpl` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

No production code in this step — the tests are intentionally red until Task 7 adds `AnalyticsServiceImpl`. (Leaving this step explicit keeps the TDD red→green ordering visible across the two tasks.)

- [ ] **Step 4: Run tests to verify they still fail (pending Task 7)**

Run: `./mvnw -q test -Dtest=AnalyticsServiceImplTest`
Expected: COMPILE FAILURE — still red; turns green in Task 7 Step 4.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/app/categorise/application/internal/AnalyticsServiceImplTest.java
git commit -m "test(analytics): add failing AnalyticsServiceImpl unit tests"
```

---

### Task 7: AnalyticsServiceImpl implementation

**Files:**
- Create: `src/main/java/com/app/categorise/application/internal/AnalyticsServiceImpl.java`

`@Service` with constructor injection of `UserTranscriptRepository` and a `Clock` (defaulted to `Clock.systemUTC()` for production via a second constructor; the test uses a fixed clock). Resolves the zone with `ZoneId.of`, falling back to UTC on null/blank/invalid (never throws). Default range = last 12 months. Granularity = WEEK when the range span is `<= 92` days (~3 months), else MONTH. Streaks are computed in-service over the repository's distinct-save-days, walked ascending in the resolved zone; the current streak's run must end today or yesterday in that zone.

- [ ] **Step 1: Write the implementation (tests already written in Task 6)**

Create `src/main/java/com/app/categorise/application/internal/AnalyticsServiceImpl.java`:

```java
package com.app.categorise.application.internal;

import com.app.categorise.api.dto.analytics.AnalyticsDto;
import com.app.categorise.api.dto.analytics.AnalyticsDto.CategoryCount;
import com.app.categorise.api.dto.analytics.AnalyticsDto.CreatorCount;
import com.app.categorise.api.dto.analytics.AnalyticsDto.PeriodGranularity;
import com.app.categorise.api.dto.analytics.AnalyticsDto.PlatformCount;
import com.app.categorise.api.dto.analytics.AnalyticsDto.SavedPerPeriod;
import com.app.categorise.data.repository.AnalyticsProjections;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.domain.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Compute-on-read analytics assembly (mirrors {@code UsageServiceImpl}).
 *
 * <p>Resolves the client IANA timezone (fallback UTC, §5.5), the default 12-month range,
 * series granularity, breakdown bucketing, and in-service streak computation, then
 * assembles a single {@link AnalyticsDto}.</p>
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsServiceImpl.class);

    /** Ranges up to ~3 months use weekly buckets; longer ranges use monthly buckets. */
    private static final long WEEK_GRANULARITY_MAX_DAYS = 92L;

    /** Number of creators surfaced in {@code topCreators}. */
    private static final int TOP_CREATORS_LIMIT = 10;

    private static final String UNCATEGORISED = "Uncategorised";
    private static final String UNKNOWN = "Unknown";

    private final UserTranscriptRepository repository;
    private final Clock clock;

    public AnalyticsServiceImpl(UserTranscriptRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public AnalyticsServiceImpl(UserTranscriptRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public AnalyticsDto getUserAnalytics(UUID userId, String tz, Instant from, Instant to) {
        ZoneId zone = resolveZone(tz);
        String zoneId = zone.getId();

        Instant rangeTo = (to != null) ? to : Instant.now(clock);
        Instant rangeFrom = (from != null)
                ? from
                : rangeTo.atZone(zone).minusMonths(12).toInstant();

        PeriodGranularity granularity = selectGranularity(rangeFrom, rangeTo);
        String granularityUnit = (granularity == PeriodGranularity.WEEK) ? "week" : "month";

        long totalTranscripts = repository.countSavesInRange(userId, rangeFrom, rangeTo);
        double totalDurationSeconds = repository.sumDurationInRange(userId, rangeFrom, rangeTo);

        List<SavedPerPeriod> savedPerPeriod = repository
                .savedPerPeriod(userId, granularityUnit, zoneId, rangeFrom, rangeTo)
                .stream()
                .map(p -> new SavedPerPeriod(p.getPeriodStart(), p.getCount()))
                .toList();

        List<CategoryCount> byCategory = repository
                .countByCategoryInRange(userId, rangeFrom, rangeTo)
                .stream()
                .map(c -> new CategoryCount(
                        c.getCategoryId(),
                        c.getCategoryId() == null ? UNCATEGORISED : c.getName(),
                        c.getCount()))
                .toList();

        List<PlatformCount> byPlatform = repository
                .countByPlatformInRange(userId, rangeFrom, rangeTo)
                .stream()
                .map(p -> new PlatformCount(blankToUnknown(p.getPlatform()), p.getCount()))
                .toList();

        List<CreatorCount> topCreators = repository
                .countByCreatorInRange(userId, rangeFrom, rangeTo)
                .stream()
                .limit(TOP_CREATORS_LIMIT)
                .map(c -> new CreatorCount(
                        blankToUnknown(c.getAccount()), c.getAccountId(), c.getCount()))
                .toList();

        List<LocalDate> saveDays = repository.distinctSaveDays(userId, zoneId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int currentStreak = computeCurrentStreak(saveDays, today);
        int longestStreak = computeLongestStreak(saveDays);

        return new AnalyticsDto(
                totalTranscripts,
                totalDurationSeconds,
                currentStreak,
                longestStreak,
                rangeFrom,
                rangeTo,
                zoneId,
                granularity,
                savedPerPeriod,
                byCategory,
                byPlatform,
                topCreators);
    }

    /** Resolve the IANA zone; null/blank/invalid all fall back to UTC (never throws). */
    private ZoneId resolveZone(String tz) {
        if (tz == null || tz.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(tz.trim());
        } catch (Exception e) {
            logger.warn("Invalid timezone '{}' supplied to analytics; falling back to UTC", tz);
            return ZoneId.of("UTC");
        }
    }

    private PeriodGranularity selectGranularity(Instant from, Instant to) {
        long days = ChronoUnit.DAYS.between(from, to);
        return days <= WEEK_GRANULARITY_MAX_DAYS
                ? PeriodGranularity.WEEK
                : PeriodGranularity.MONTH;
    }

    private String blankToUnknown(String value) {
        return (value == null || value.isBlank()) ? UNKNOWN : value;
    }

    /**
     * Current streak = length of the consecutive-day run that ends on {@code today} or
     * {@code today - 1} (a save yesterday but not yet today still counts as current).
     * Returns 0 when the most recent save-day is older than yesterday or the list is empty.
     * {@code saveDays} is assumed ascending and distinct (as returned by the repository).
     */
    private int computeCurrentStreak(List<LocalDate> saveDays, LocalDate today) {
        if (saveDays.isEmpty()) {
            return 0;
        }
        LocalDate last = saveDays.get(saveDays.size() - 1);
        if (last.isBefore(today.minusDays(1))) {
            return 0;
        }
        int streak = 1;
        for (int i = saveDays.size() - 1; i > 0; i--) {
            LocalDate current = saveDays.get(i);
            LocalDate previous = saveDays.get(i - 1);
            if (previous.equals(current.minusDays(1))) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * Longest consecutive-day run anywhere in the (all-time) ascending save-day list.
     * Independent of {@code today} and of the dashboard range.
     */
    private int computeLongestStreak(List<LocalDate> saveDays) {
        if (saveDays.isEmpty()) {
            return 0;
        }
        int longest = 1;
        int run = 1;
        for (int i = 1; i < saveDays.size(); i++) {
            if (saveDays.get(i - 1).equals(saveDays.get(i).minusDays(1))) {
                run++;
            } else {
                run = 1;
            }
            longest = Math.max(longest, run);
        }
        return longest;
    }
}
```

- [ ] **Step 2: (no separate failing-test step — tests authored in Task 6)**

The red tests already exist from Task 6 Step 1.

- [ ] **Step 3: Confirm wiring**

No additional wiring needed — `@Service` + constructor injection auto-registers the bean; the single-arg constructor is used by Spring, the two-arg constructor by the unit test.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=AnalyticsServiceImplTest`
Expected: PASS — all `getUserAnalytics` unit tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/application/internal/AnalyticsServiceImpl.java
git commit -m "feat(analytics): implement AnalyticsServiceImpl (range, granularity, streaks, bucketing)"
```

---

## Chunk 3: Backend API layer — controller

### Task 8: AnalyticsController (`GET /api/v1/analytics`)

**Files:**
- Create: `src/main/java/com/app/categorise/api/controller/AnalyticsController.java`
- Create: `src/test/java/com/app/categorise/api/controller/AnalyticsControllerTest.java`

New dedicated controller at `@RequestMapping("/api/v1")` with `GET /analytics` (per §6.1). userId is resolved from `@AuthenticationPrincipal UserPrincipal` via a private `requireUser(...)` helper mirroring `TranscriptController`, so an unauthenticated call fails fast. `from`/`to` parse with `@DateTimeFormat(ISO.DATE_TIME)`; `tz` is the raw IANA string passed straight to the service (which resolves/falls back). Missing `tz` ⇒ service echoes `"UTC"`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/app/categorise/api/controller/AnalyticsControllerTest.java`:

```java
package com.app.categorise.api.controller;

import com.app.categorise.api.dto.analytics.AnalyticsDto;
import com.app.categorise.domain.service.AnalyticsService;
import com.app.categorise.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AnalyticsController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    private UUID userId;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userPrincipal = new UserPrincipal(
            userId,
            "Test User",
            "test@example.com",
            "test@example.com",
            null,
            Collections.emptyList());
    }

    private RequestPostProcessor authenticatedUser() {
        return request -> {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    private AnalyticsDto emptyDto(String timezone) {
        return new AnalyticsDto(
                0L, 0.0, 0, 0,
                Instant.parse("2025-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T00:00:00Z"),
                timezone,
                AnalyticsDto.PeriodGranularity.MONTH,
                List.of(), List.of(), List.of(), List.of());
    }

    @Nested
    @DisplayName("GET /api/v1/analytics")
    class GetAnalytics {

        @Test
        @DisplayName("Should pass null tz through and echo UTC when tz is missing")
        void getAnalytics_withoutTz_passesNullAndEchoesUtc() throws Exception {
            when(analyticsService.getUserAnalytics(eq(userId), isNull(), any(), any()))
                    .thenReturn(emptyDto("UTC"));

            mockMvc.perform(get("/api/v1/analytics").with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timezone").value("UTC"))
                    .andExpect(jsonPath("$.totalTranscripts").value(0));

            verify(analyticsService).getUserAnalytics(eq(userId), isNull(), isNull(), isNull());
        }

        @Test
        @DisplayName("Should forward tz, from, and to to the service")
        void getAnalytics_withParams_forwardsThemToService() throws Exception {
            Instant from = Instant.parse("2026-01-01T00:00:00Z");
            Instant to = Instant.parse("2026-06-01T00:00:00Z");
            when(analyticsService.getUserAnalytics(eq(userId), eq("America/New_York"), eq(from), eq(to)))
                    .thenReturn(emptyDto("America/New_York"));

            mockMvc.perform(get("/api/v1/analytics")
                            .param("tz", "America/New_York")
                            .param("from", "2026-01-01T00:00:00Z")
                            .param("to", "2026-06-01T00:00:00Z")
                            .with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timezone").value("America/New_York"));

            verify(analyticsService).getUserAnalytics(eq(userId), eq("America/New_York"), eq(from), eq(to));
        }

        @Test
        @DisplayName("Should return a populated analytics payload")
        void getAnalytics_populatedResponse_serialisesAllSections() throws Exception {
            UUID catId = UUID.randomUUID();
            AnalyticsDto dto = new AnalyticsDto(
                    12L, 7200.0, 3, 5,
                    Instant.parse("2025-06-28T00:00:00Z"),
                    Instant.parse("2026-06-28T00:00:00Z"),
                    "UTC",
                    AnalyticsDto.PeriodGranularity.MONTH,
                    List.of(new AnalyticsDto.SavedPerPeriod(Instant.parse("2026-01-01T00:00:00Z"), 4L)),
                    List.of(new AnalyticsDto.CategoryCount(catId, "Technology", 7L)),
                    List.of(new AnalyticsDto.PlatformCount("youtube", 9L)),
                    List.of(new AnalyticsDto.CreatorCount("Some Channel", "acct-1", 4L)));
            when(analyticsService.getUserAnalytics(eq(userId), any(), any(), any())).thenReturn(dto);

            mockMvc.perform(get("/api/v1/analytics").with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTranscripts").value(12))
                    .andExpect(jsonPath("$.totalDurationSeconds").value(7200.0))
                    .andExpect(jsonPath("$.currentStreakDays").value(3))
                    .andExpect(jsonPath("$.longestStreakDays").value(5))
                    .andExpect(jsonPath("$.granularity").value("MONTH"))
                    .andExpect(jsonPath("$.savedPerPeriod[0].count").value(4))
                    .andExpect(jsonPath("$.byCategory[0].name").value("Technology"))
                    .andExpect(jsonPath("$.byPlatform[0].platform").value("youtube"))
                    .andExpect(jsonPath("$.topCreators[0].account").value("Some Channel"))
                    .andExpect(jsonPath("$.topCreators[0].accountId").value("acct-1"));
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=AnalyticsControllerTest`
Expected: COMPILE FAILURE — `AnalyticsController` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/app/categorise/api/controller/AnalyticsController.java`:

```java
package com.app.categorise.api.controller;

import com.app.categorise.api.dto.analytics.AnalyticsDto;
import com.app.categorise.domain.service.AnalyticsService;
import com.app.categorise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only personal analytics endpoint (compute-on-read).
 *
 * <p>Dedicated controller (not part of {@code TranscriptController}) under the versioned
 * {@code /api/v1} namespace, per design §6.1.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Analytics", description = "Read-only personal analytics dashboard")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "Get the authenticated user's analytics dashboard")
    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsDto> getAnalytics(
            Authentication authentication,
            @RequestParam(required = false) String tz,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        UUID userId = requireUser(authentication);
        return ResponseEntity.ok(analyticsService.getUserAnalytics(userId, tz, from, to));
    }

    private UUID requireUser(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalArgumentException("User not authenticated");
        }
        if (principal.getId() == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        return principal.getId();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=AnalyticsControllerTest`
Expected: PASS — all 3 controller tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/api/controller/AnalyticsController.java \
        src/test/java/com/app/categorise/api/controller/AnalyticsControllerTest.java
git commit -m "feat(analytics): add AnalyticsController GET /api/v1/analytics"
```

---

## Chunk 4: iOS data layer — response, domain model, service

### Task 9: AnalyticsResponse + Analytics domain model + mapper

**Files:**
- Create: `Scoop/models/api/AnalyticsResponse.swift`
- Create: `Scoop/models/domain/Analytics.swift`
- Create: `ScoopTests/AnalyticsResponseTests.swift`

`AnalyticsResponse` is a `Decodable` mirror of `AnalyticsDto` with nested `Decodable` structs (mirroring `UsageInfoResponse`). The `PeriodGranularity` enum decodes from the backend's `"WEEK"`/`"MONTH"` strings. `Analytics` is the UI-facing domain struct with a `toAnalytics` mapper (mirroring `UsageInfoResponse` → `UsageInfo`). `Instant` fields serialise as ISO-8601 strings from the backend, decoded into `Date` via `ISO8601DateFormatter` in the mapper (the `HTTPClient`'s default `JSONDecoder` does not assume a date strategy, so we decode the raw strings explicitly to stay robust to fractional seconds).

- [ ] **Step 1: Write the failing tests**

Create `ScoopTests/AnalyticsResponseTests.swift`:

```swift
import Foundation
import Testing
@testable import Scoop

extension ScoopTestSuiteContainer {
@Suite(.serialized)
struct AnalyticsResponseTests {

    private func decode(_ json: String) throws -> AnalyticsResponse {
        try JSONDecoder().decode(AnalyticsResponse.self, from: Data(json.utf8))
    }

    @Test func decodesPopulatedPayload() throws {
        let json = """
        {
          "totalTranscripts": 12,
          "totalDurationSeconds": 7200.0,
          "currentStreakDays": 3,
          "longestStreakDays": 5,
          "rangeFrom": "2025-06-28T00:00:00Z",
          "rangeTo": "2026-06-28T00:00:00Z",
          "timezone": "America/New_York",
          "granularity": "MONTH",
          "savedPerPeriod": [{"periodStart":"2026-01-01T00:00:00Z","count":4}],
          "byCategory": [{"categoryId":"11111111-1111-1111-1111-111111111111","name":"Technology","count":7}],
          "byPlatform": [{"platform":"youtube","count":9}],
          "topCreators": [{"account":"Some Channel","accountId":"acct-1","count":4}]
        }
        """
        let response = try decode(json)

        #expect(response.totalTranscripts == 12)
        #expect(response.totalDurationSeconds == 7200.0)
        #expect(response.currentStreakDays == 3)
        #expect(response.longestStreakDays == 5)
        #expect(response.timezone == "America/New_York")
        #expect(response.granularity == .month)
        #expect(response.savedPerPeriod.first?.count == 4)
        #expect(response.byCategory.first?.name == "Technology")
        #expect(response.byPlatform.first?.platform == "youtube")
        #expect(response.topCreators.first?.account == "Some Channel")
        #expect(response.topCreators.first?.accountId == "acct-1")
    }

    @Test func decodesZeroUserEmpties() throws {
        let json = """
        {
          "totalTranscripts": 0,
          "totalDurationSeconds": 0.0,
          "currentStreakDays": 0,
          "longestStreakDays": 0,
          "rangeFrom": "2025-06-28T00:00:00Z",
          "rangeTo": "2026-06-28T00:00:00Z",
          "timezone": "UTC",
          "granularity": "MONTH",
          "savedPerPeriod": [],
          "byCategory": [],
          "byPlatform": [],
          "topCreators": []
        }
        """
        let response = try decode(json)

        #expect(response.totalTranscripts == 0)
        #expect(response.timezone == "UTC")
        #expect(response.savedPerPeriod.isEmpty)
        #expect(response.byCategory.isEmpty)
        #expect(response.byPlatform.isEmpty)
        #expect(response.topCreators.isEmpty)
    }

    @Test func decodesNullCategoryIdAndAccountId() throws {
        let json = """
        {
          "totalTranscripts": 4,
          "totalDurationSeconds": 0.0,
          "currentStreakDays": 0,
          "longestStreakDays": 0,
          "rangeFrom": "2025-06-28T00:00:00Z",
          "rangeTo": "2026-06-28T00:00:00Z",
          "timezone": "UTC",
          "granularity": "WEEK",
          "savedPerPeriod": [],
          "byCategory": [{"categoryId":null,"name":"Uncategorised","count":3}],
          "byPlatform": [{"platform":"Unknown","count":1}],
          "topCreators": [{"account":"Unknown","accountId":null,"count":6}]
        }
        """
        let response = try decode(json)

        #expect(response.granularity == .week)
        #expect(response.byCategory.first?.categoryId == nil)
        #expect(response.byCategory.first?.name == "Uncategorised")
        #expect(response.topCreators.first?.accountId == nil)
    }

    @Test func mapsResponseToDomain() throws {
        let json = """
        {
          "totalTranscripts": 2,
          "totalDurationSeconds": 90.0,
          "currentStreakDays": 1,
          "longestStreakDays": 2,
          "rangeFrom": "2025-06-28T00:00:00Z",
          "rangeTo": "2026-06-28T00:00:00Z",
          "timezone": "UTC",
          "granularity": "MONTH",
          "savedPerPeriod": [{"periodStart":"2026-01-01T00:00:00Z","count":2}],
          "byCategory": [],
          "byPlatform": [],
          "topCreators": []
        }
        """
        let analytics = try decode(json).toAnalytics

        #expect(analytics.totalTranscripts == 2)
        #expect(analytics.totalDurationSeconds == 90.0)
        #expect(analytics.timezone == "UTC")
        #expect(analytics.granularity == .month)
        #expect(analytics.savedPerPeriod.count == 1)
        #expect(analytics.savedPerPeriod.first?.count == 2)
        #expect(analytics.savedPerPeriod.first?.periodStart
            == ISO8601DateFormatter().date(from: "2026-01-01T00:00:00Z"))
    }
}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run (from `/Users/apushpavannan/personal/TranscribeAssistant-ios`):
`xcodebuild test -scheme Scoop -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:ScoopTests/AnalyticsResponseTests`
Expected: BUILD FAILURE — `AnalyticsResponse`/`Analytics` do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `Scoop/models/api/AnalyticsResponse.swift`:

```swift
import Foundation

/// Decodable mirror of the backend `AnalyticsDto` (and nested records).
struct AnalyticsResponse: Decodable {
    let totalTranscripts: Int
    let totalDurationSeconds: Double
    let currentStreakDays: Int
    let longestStreakDays: Int
    let rangeFrom: String
    let rangeTo: String
    let timezone: String
    let granularity: PeriodGranularity
    let savedPerPeriod: [SavedPerPeriod]
    let byCategory: [CategoryCount]
    let byPlatform: [PlatformCount]
    let topCreators: [CreatorCount]

    enum PeriodGranularity: String, Decodable {
        case week = "WEEK"
        case month = "MONTH"
    }

    struct SavedPerPeriod: Decodable {
        let periodStart: String
        let count: Int
    }

    struct CategoryCount: Decodable {
        let categoryId: String?
        let name: String
        let count: Int
    }

    struct PlatformCount: Decodable {
        let platform: String
        let count: Int
    }

    struct CreatorCount: Decodable {
        let account: String
        let accountId: String?
        let count: Int
    }
}

extension AnalyticsResponse {
    /// Map the wire response into the UI-facing `Analytics` domain model.
    var toAnalytics: Analytics {
        let formatter = ISO8601DateFormatter()
        let fallback = Date(timeIntervalSince1970: 0)
        return Analytics(
            totalTranscripts: totalTranscripts,
            totalDurationSeconds: totalDurationSeconds,
            currentStreakDays: currentStreakDays,
            longestStreakDays: longestStreakDays,
            rangeFrom: formatter.date(from: rangeFrom) ?? fallback,
            rangeTo: formatter.date(from: rangeTo) ?? fallback,
            timezone: timezone,
            granularity: granularity == .week ? .week : .month,
            savedPerPeriod: savedPerPeriod.map {
                Analytics.SavedPeriod(
                    periodStart: formatter.date(from: $0.periodStart) ?? fallback,
                    count: $0.count)
            },
            byCategory: byCategory.map {
                Analytics.CategoryCount(categoryId: $0.categoryId, name: $0.name, count: $0.count)
            },
            byPlatform: byPlatform.map {
                Analytics.PlatformCount(platform: $0.platform, count: $0.count)
            },
            topCreators: topCreators.map {
                Analytics.CreatorCount(account: $0.account, accountId: $0.accountId, count: $0.count)
            })
    }
}
```

Create `Scoop/models/domain/Analytics.swift`:

```swift
import Foundation

/// UI-facing analytics model (mirrors `UsageInfo`'s role for the Subscription slice).
struct Analytics {
    let totalTranscripts: Int
    let totalDurationSeconds: Double
    let currentStreakDays: Int
    let longestStreakDays: Int
    let rangeFrom: Date
    let rangeTo: Date
    let timezone: String
    let granularity: Granularity
    let savedPerPeriod: [SavedPeriod]
    let byCategory: [CategoryCount]
    let byPlatform: [PlatformCount]
    let topCreators: [CreatorCount]

    enum Granularity { case week, month }

    struct SavedPeriod: Identifiable {
        let periodStart: Date
        let count: Int
        var id: Date { periodStart }
    }

    struct CategoryCount: Identifiable {
        let categoryId: String?
        let name: String
        let count: Int
        var id: String { categoryId ?? "uncategorised" }
    }

    struct PlatformCount: Identifiable {
        let platform: String
        let count: Int
        var id: String { platform }
    }

    struct CreatorCount: Identifiable {
        let account: String
        let accountId: String?
        let count: Int
        var id: String { accountId ?? account }
    }

    /// True when the user has no saved content in the selected range and no streak history.
    var isEmpty: Bool {
        totalTranscripts == 0 && savedPerPeriod.isEmpty && longestStreakDays == 0
    }

    /// "Content saved" formatted as "Xh Ym" (factual total source length; never "time saved").
    var formattedDuration: String {
        let totalSeconds = Int(totalDurationSeconds)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        return "\(hours)h \(minutes)m"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `xcodebuild test -scheme Scoop -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:ScoopTests/AnalyticsResponseTests`
Expected: PASS — all decoding + mapping tests green.

- [ ] **Step 5: Commit**

```bash
git add Scoop/models/api/AnalyticsResponse.swift \
        Scoop/models/domain/Analytics.swift \
        ScoopTests/AnalyticsResponseTests.swift
git commit -m "feat(analytics): add AnalyticsResponse, Analytics domain model, and mapper (iOS)"
```

---

### Task 10: AnalyticsService (iOS)

**Files:**
- Create: `Scoop/service/AnalyticsService.swift`
- Create: `ScoopTests/AnalyticsServiceTests.swift`

Mirrors `SubscriptionService`: a static `client: HTTPClient!`, a `configure(client:)`, and `getAnalytics(from:to:)` that builds `"/api/v1/analytics?tz=...&from=...&to=..."`. The `tz` query item is always set from `TimeZone.current.identifier`; `from`/`to` are optional ISO-8601 query items. Decodes `AnalyticsResponse` via the shared `HTTPClient.request` and returns `response.toAnalytics`.

- [ ] **Step 1: Write the failing tests**

Create `ScoopTests/AnalyticsServiceTests.swift`:

```swift
import Foundation
import Testing
@testable import Scoop

extension ScoopTestSuiteContainer {
@Suite(.serialized)
struct AnalyticsServiceTests {

    private func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        return URLSession(configuration: config)
    }

    private let emptyJSON = """
    {
      "totalTranscripts": 0,
      "totalDurationSeconds": 0.0,
      "currentStreakDays": 0,
      "longestStreakDays": 0,
      "rangeFrom": "2025-06-28T00:00:00Z",
      "rangeTo": "2026-06-28T00:00:00Z",
      "timezone": "UTC",
      "granularity": "MONTH",
      "savedPerPeriod": [],
      "byCategory": [],
      "byPlatform": [],
      "topCreators": []
    }
    """

    @Test func getAnalytics_hitsAnalyticsPathWithTimezoneQuery() async throws {
        let session = makeSession()
        let base = URL(string: "https://example.com")!
        AnalyticsService.configure(client: HTTPClient(baseURL: base, session: session))

        MockURLProtocol.requestHandler = { req in
            #expect(req.url?.path == "/api/v1/analytics")
            let query = req.url?.query ?? ""
            #expect(query.contains("tz="))
            let http = HTTPURLResponse(
                url: req.url!, statusCode: 200, httpVersion: nil,
                headerFields: ["Content-Type": "application/json"])!
            return (http, Data(self.emptyJSON.utf8))
        }

        let analytics = try await AnalyticsService.getAnalytics()
        #expect(analytics.totalTranscripts == 0)
        #expect(analytics.timezone == "UTC")
    }

    @Test func getAnalytics_includesFromAndToWhenProvided() async throws {
        let session = makeSession()
        let base = URL(string: "https://example.com")!
        AnalyticsService.configure(client: HTTPClient(baseURL: base, session: session))

        let from = ISO8601DateFormatter().date(from: "2026-01-01T00:00:00Z")!
        let to = ISO8601DateFormatter().date(from: "2026-06-01T00:00:00Z")!

        MockURLProtocol.requestHandler = { req in
            let query = req.url?.query ?? ""
            #expect(query.contains("from="))
            #expect(query.contains("to="))
            let http = HTTPURLResponse(
                url: req.url!, statusCode: 200, httpVersion: nil,
                headerFields: ["Content-Type": "application/json"])!
            return (http, Data(self.emptyJSON.utf8))
        }

        let analytics = try await AnalyticsService.getAnalytics(from: from, to: to)
        #expect(analytics.totalTranscripts == 0)
    }

    @Test func getAnalytics_decodesPopulatedResponse() async throws {
        let session = makeSession()
        let base = URL(string: "https://example.com")!
        AnalyticsService.configure(client: HTTPClient(baseURL: base, session: session))

        let populated = """
        {
          "totalTranscripts": 5,
          "totalDurationSeconds": 600.0,
          "currentStreakDays": 2,
          "longestStreakDays": 4,
          "rangeFrom": "2025-06-28T00:00:00Z",
          "rangeTo": "2026-06-28T00:00:00Z",
          "timezone": "America/New_York",
          "granularity": "WEEK",
          "savedPerPeriod": [{"periodStart":"2026-06-01T00:00:00Z","count":5}],
          "byCategory": [{"categoryId":null,"name":"Uncategorised","count":5}],
          "byPlatform": [{"platform":"youtube","count":5}],
          "topCreators": [{"account":"Chan","accountId":"a1","count":5}]
        }
        """

        MockURLProtocol.requestHandler = { req in
            let http = HTTPURLResponse(
                url: req.url!, statusCode: 200, httpVersion: nil,
                headerFields: ["Content-Type": "application/json"])!
            return (http, Data(populated.utf8))
        }

        let analytics = try await AnalyticsService.getAnalytics()
        #expect(analytics.totalTranscripts == 5)
        #expect(analytics.granularity == .week)
        #expect(analytics.byCategory.first?.name == "Uncategorised")
        #expect(analytics.topCreators.first?.accountId == "a1")
    }
}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `xcodebuild test -scheme Scoop -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:ScoopTests/AnalyticsServiceTests`
Expected: BUILD FAILURE — `AnalyticsService` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `Scoop/service/AnalyticsService.swift`:

```swift
import Foundation

/// Service for analytics-related API calls. Mirrors `SubscriptionService`.
class AnalyticsService {

    /// Exposed for app to configure once at startup, or tests to inject.
    static var client: HTTPClient!

    static func configure(client: HTTPClient) {
        self.client = client
    }

    /// Fetch the authenticated user's analytics dashboard.
    /// - Parameters:
    ///   - from: optional inclusive range start
    ///   - to: optional inclusive range end
    /// - Returns: the mapped `Analytics` domain model
    static func getAnalytics(from: Date? = nil, to: Date? = nil) async throws -> Analytics {
        guard let client = Self.client else {
            throw NetworkError.requestFailed
        }

        var components = URLComponents()
        components.path = "/api/v1/analytics"

        let formatter = ISO8601DateFormatter()
        var items: [URLQueryItem] = [
            URLQueryItem(name: "tz", value: TimeZone.current.identifier)
        ]
        if let from = from {
            items.append(URLQueryItem(name: "from", value: formatter.string(from: from)))
        }
        if let to = to {
            items.append(URLQueryItem(name: "to", value: formatter.string(from: to)))
        }
        components.queryItems = items

        let path = components.string ?? "/api/v1/analytics?tz=\(TimeZone.current.identifier)"

        let response: AnalyticsResponse = try await client.request(path: path, method: .get)
        return response.toAnalytics
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `xcodebuild test -scheme Scoop -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:ScoopTests/AnalyticsServiceTests`
Expected: PASS — query-building + decoding tests green.

- [ ] **Step 5: Commit**

```bash
git add Scoop/service/AnalyticsService.swift \
        ScoopTests/AnalyticsServiceTests.swift
git commit -m "feat(analytics): add AnalyticsService over HTTPClient (iOS)"
```

---

## Chunk 5: OPTIONAL backend index (only if query plans show seq scans)

### Task 11: (OPTIONAL) V29 composite `(user_id, created_at)` index

> **OPTIONAL — do not implement unless `EXPLAIN ANALYZE` shows sequential scans on
> `user_transcripts` for the analytics queries.** Ship compute-on-read first (Tasks 1–10);
> add this only as a measured optimisation (§6.4, §11.6). This is migration **V29**, the next
> free slot above the Collections `V28`.

**Files:**
- Create: `src/main/resources/db/migration/V29__add_user_transcripts_user_created_at_index.sql`

**How to decide whether this task is needed (inspect the plan first):**

1. Start the app against a representative dataset, then run `EXPLAIN ANALYZE` on the hottest analytics query (the range count) using a real user id:
   ```sql
   EXPLAIN ANALYZE
   SELECT COUNT(*) FROM user_transcripts
   WHERE user_id = '<uuid>' AND created_at BETWEEN '2025-06-28' AND '2026-06-28';
   ```
   And the saved-per-period bucketing:
   ```sql
   EXPLAIN ANALYZE
   SELECT date_trunc('month', created_at AT TIME ZONE 'UTC') AS period_start, COUNT(*)
   FROM user_transcripts
   WHERE user_id = '<uuid>' AND created_at BETWEEN '2025-06-28' AND '2026-06-28'
   GROUP BY period_start ORDER BY period_start;
   ```
2. If the plan shows `Seq Scan on user_transcripts` (rather than an `Index Scan`/`Bitmap Index Scan` using `idx_user_transcripts_user_id`), proceed with Steps 1–5 below. If it already uses an index scan, **skip this task** and record the decision in the PR description.

- [ ] **Step 1: Write the failing test**

Append a Testcontainers test to `src/test/java/com/app/categorise/data/repository/UserTranscriptRepositoryTest.java` asserting the index exists (it reads `pg_indexes` against the live container DB):

```java
    @Test
    void migration_createsUserCreatedAtCompositeIndex() {
        @SuppressWarnings("unchecked")
        List<String> indexes = entityManager.getEntityManager()
                .createNativeQuery(
                        "SELECT indexname FROM pg_indexes " +
                        "WHERE tablename = 'user_transcripts'")
                .getResultList()
                .stream()
                .map(Object::toString)
                .toList();

        assertThat(indexes).contains("idx_user_transcripts_user_created_at");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=UserTranscriptRepositoryTest#migration_createsUserCreatedAtCompositeIndex`
Expected: FAIL — the index does not exist (migration not yet added).

- [ ] **Step 3: Write minimal implementation**

Create `src/main/resources/db/migration/V29__add_user_transcripts_user_created_at_index.sql`:

```sql
-- Composite index supporting analytics range scans and date_trunc bucketing on
-- user_transcripts. Optional v1 optimisation: only added because EXPLAIN ANALYZE
-- showed sequential scans for the per-user range/bucketing analytics queries.
-- See design spec 2026-06-26-personal-analytics-dashboard-design.md §6.4.
CREATE INDEX IF NOT EXISTS idx_user_transcripts_user_created_at
    ON user_transcripts (user_id, created_at);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=UserTranscriptRepositoryTest#migration_createsUserCreatedAtCompositeIndex`
Expected: PASS — Flyway applies V29 in the Testcontainers DB and the index is present.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V29__add_user_transcripts_user_created_at_index.sql \
        src/test/java/com/app/categorise/data/repository/UserTranscriptRepositoryTest.java
git commit -m "perf(analytics): add optional (user_id, created_at) composite index (V29)"
```

---

## Chunk 6: iOS presentation layer — view model, screen, navigation

### Task 12: AnalyticsViewModel (loading/loaded/empty/error states)

**Files:**
- Create: `Scoop/viewModel/AnalyticsViewModel.swift`
- Create: `ScoopTests/AnalyticsViewModelTests.swift`

`@MainActor ObservableObject` mirroring `SubscriptionViewModel`'s shape, exposing an explicit
`AnalyticsLoadingState` enum (`loading`/`loaded(Analytics)`/`empty`/`error(String)`). `load()`
calls `AnalyticsService.getAnalytics()` and maps the result to `.empty` when `analytics.isEmpty`
else `.loaded`; failures map to `.error`. A `loader` closure is injectable for tests so they do
not depend on the static `AnalyticsService.client`.

- [ ] **Step 1: Write the failing tests**

Create `ScoopTests/AnalyticsViewModelTests.swift`:

```swift
import Foundation
import Testing
@testable import Scoop

extension ScoopTestSuiteContainer {
@Suite(.serialized) @MainActor
struct AnalyticsViewModelTests {

    private func populatedAnalytics() -> Analytics {
        Analytics(
            totalTranscripts: 5,
            totalDurationSeconds: 600,
            currentStreakDays: 2,
            longestStreakDays: 4,
            rangeFrom: Date(timeIntervalSince1970: 0),
            rangeTo: Date(timeIntervalSince1970: 1000),
            timezone: "UTC",
            granularity: .month,
            savedPerPeriod: [Analytics.SavedPeriod(periodStart: Date(timeIntervalSince1970: 0), count: 5)],
            byCategory: [],
            byPlatform: [],
            topCreators: [])
    }

    private func emptyAnalytics() -> Analytics {
        Analytics(
            totalTranscripts: 0,
            totalDurationSeconds: 0,
            currentStreakDays: 0,
            longestStreakDays: 0,
            rangeFrom: Date(timeIntervalSince1970: 0),
            rangeTo: Date(timeIntervalSince1970: 1000),
            timezone: "UTC",
            granularity: .month,
            savedPerPeriod: [],
            byCategory: [],
            byPlatform: [],
            topCreators: [])
    }

    @Test func initialState_isLoading() {
        let vm = AnalyticsViewModel(loader: { self.populatedAnalytics() })
        if case .loading = vm.state { } else { Issue.record("Expected initial .loading state") }
    }

    @Test func load_success_transitionsToLoaded() async {
        let vm = AnalyticsViewModel(loader: { self.populatedAnalytics() })
        await vm.load()
        guard case let .loaded(analytics) = vm.state else {
            Issue.record("Expected .loaded state"); return
        }
        #expect(analytics.totalTranscripts == 5)
    }

    @Test func load_emptyAnalytics_transitionsToEmpty() async {
        let vm = AnalyticsViewModel(loader: { self.emptyAnalytics() })
        await vm.load()
        if case .empty = vm.state { } else { Issue.record("Expected .empty state") }
    }

    @Test func load_failure_transitionsToError() async {
        let vm = AnalyticsViewModel(loader: { throw NetworkError.requestFailed })
        await vm.load()
        if case .error = vm.state { } else { Issue.record("Expected .error state") }
    }

    @Test func load_resetsToLoadingBeforeFetching() async {
        let vm = AnalyticsViewModel(loader: { throw NetworkError.requestFailed })
        await vm.load() // -> error
        if case .error = vm.state { } else { Issue.record("Expected .error after first load") }

        // A subsequent successful load recovers to .loaded.
        vm.loader = { self.populatedAnalytics() }
        await vm.load()
        if case .loaded = vm.state { } else { Issue.record("Expected .loaded after retry") }
    }
}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `xcodebuild test -scheme Scoop -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:ScoopTests/AnalyticsViewModelTests`
Expected: BUILD FAILURE — `AnalyticsViewModel`/`AnalyticsLoadingState` do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `Scoop/viewModel/AnalyticsViewModel.swift`:

```swift
import Foundation

/// Explicit UI states for the Insights screen.
enum AnalyticsLoadingState {
    case loading
    case loaded(Analytics)
    case empty
    case error(String)
}

/// View model backing `InsightsScreen`. Mirrors `SubscriptionViewModel`'s `@MainActor`
/// `ObservableObject` shape, but with an explicit state enum for the four UI states.
@MainActor
final class AnalyticsViewModel: ObservableObject {

    @Published var state: AnalyticsLoadingState = .loading

    /// Injectable loader so tests do not depend on the static `AnalyticsService.client`.
    var loader: () async throws -> Analytics

    init(loader: @escaping () async throws -> Analytics = { try await AnalyticsService.getAnalytics() }) {
        self.loader = loader
    }

    /// Fetch analytics and resolve to loaded/empty/error.
    func load() async {
        state = .loading
        do {
            let analytics = try await loader()
            state = analytics.isEmpty ? .empty : .loaded(analytics)
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `xcodebuild test -scheme Scoop -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:ScoopTests/AnalyticsViewModelTests`
Expected: PASS — all state-transition tests green.

- [ ] **Step 5: Commit**

```bash
git add Scoop/viewModel/AnalyticsViewModel.swift \
        ScoopTests/AnalyticsViewModelTests.swift
git commit -m "feat(analytics): add AnalyticsViewModel with loading/loaded/empty/error states (iOS)"
```

---

### Task 13: InsightsScreen (Swift Charts) + ProfileScreen entry point

**Files:**
- Create: `Scoop/views/Insights/InsightsScreen.swift`
- Modify: `Scoop/views/Profile/ProfileScreen.swift`

`InsightsScreen` owns a `@StateObject AnalyticsViewModel`, calls `load()` on `.task`, and renders
the four states. The loaded state shows totals cards (saved count + content-saved duration +
current/longest streak), a saved-per-period bar chart via Swift Charts `BarMark`, and category /
platform / creator breakdown lists. `ProfileScreen` gains an **Insights** `ProfileActionRow`
(icon `chart.bar.fill`) above the Subscription row that sets `showInsights = true` and presents
`InsightsScreen` via `.sheet`, matching the existing sheet-based pattern (no `NavigationLink`).

- [ ] **Step 1: Write the failing test**

This task is primarily SwiftUI view code; the behavioural state logic is covered by Task 12. Add
a lightweight build-smoke test that constructs the screen's view model in each state so the screen
compiles against the model contract. Create `ScoopTests/InsightsScreenSmokeTests.swift`:

```swift
import Foundation
import SwiftUI
import Testing
@testable import Scoop

extension ScoopTestSuiteContainer {
@Suite(.serialized) @MainActor
struct InsightsScreenSmokeTests {

    @Test func insightsScreen_buildsWithViewModel() {
        let vm = AnalyticsViewModel(loader: {
            Analytics(
                totalTranscripts: 3,
                totalDurationSeconds: 180,
                currentStreakDays: 1,
                longestStreakDays: 2,
                rangeFrom: Date(timeIntervalSince1970: 0),
                rangeTo: Date(timeIntervalSince1970: 1000),
                timezone: "UTC",
                granularity: .month,
                savedPerPeriod: [Analytics.SavedPeriod(periodStart: Date(timeIntervalSince1970: 0), count: 3)],
                byCategory: [Analytics.CategoryCount(categoryId: nil, name: "Uncategorised", count: 3)],
                byPlatform: [Analytics.PlatformCount(platform: "youtube", count: 3)],
                topCreators: [Analytics.CreatorCount(account: "Chan", accountId: "a1", count: 3)])
        })
        // Constructing the view must not crash; body is exercised by the SwiftUI runtime in-app.
        let screen = InsightsScreen(viewModel: vm)
        #expect(screen.viewModel === vm)
    }
}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `xcodebuild test -scheme Scoop -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:ScoopTests/InsightsScreenSmokeTests`
Expected: BUILD FAILURE — `InsightsScreen` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `Scoop/views/Insights/InsightsScreen.swift`:

```swift
import SwiftUI
import Charts

/// Read-only "Your Insights" dashboard. Presented as a sheet from ProfileScreen.
struct InsightsScreen: View {

    @ObservedObject var viewModel: AnalyticsViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: AnalyticsViewModel = AnalyticsViewModel()) {
        self.viewModel = viewModel
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Your Insights")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Done") { dismiss() }
                    }
                }
                .task { await viewModel.load() }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            ProgressView("Loading insights…")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .empty:
            emptyState
        case .error(let message):
            errorState(message)
        case .loaded(let analytics):
            loadedState(analytics)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "chart.bar.xaxis")
                .font(.system(size: 44))
                .foregroundColor(AppColors.secondaryText)
            Text("Save your first video to unlock insights")
                .font(.system(size: 16, weight: .medium))
                .multilineTextAlignment(.center)
                .foregroundColor(AppColors.secondaryText)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func errorState(_ message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundColor(.red)
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundColor(AppColors.secondaryText)
            Button("Retry") {
                Task { await viewModel.load() }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func loadedState(_ analytics: Analytics) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                totalsSection(analytics)
                savedPerPeriodSection(analytics)
                breakdownSection(title: "Categories", items: analytics.byCategory.map {
                    ($0.id, $0.name, $0.count)
                })
                breakdownSection(title: "Platforms", items: analytics.byPlatform.map {
                    ($0.id, $0.platform, $0.count)
                })
                breakdownSection(title: "Top creators", items: analytics.topCreators.map {
                    ($0.id, $0.account, $0.count)
                })
            }
            .padding(20)
        }
        .background(AppColors.lightBackground)
    }

    private func totalsSection(_ analytics: Analytics) -> some View {
        HStack(spacing: 12) {
            statCard(title: "Content saved", value: "\(analytics.totalTranscripts)")
            statCard(title: "Total length", value: analytics.formattedDuration)
            statCard(title: "Streak", value: "\(analytics.currentStreakDays)d")
        }
    }

    private func statCard(title: String, value: String) -> some View {
        VStack(spacing: 6) {
            Text(value)
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(AppColors.primaryText)
            Text(title)
                .font(.system(size: 12))
                .foregroundColor(AppColors.secondaryText)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(AppColors.cardBackground)
        .cornerRadius(12)
    }

    private func savedPerPeriodSection(_ analytics: Analytics) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(analytics.granularity == .week ? "Saved per week" : "Saved per month")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(AppColors.primaryText)

            if analytics.savedPerPeriod.isEmpty {
                Text("No activity in this range")
                    .font(.system(size: 13))
                    .foregroundColor(AppColors.secondaryText)
            } else {
                Chart(analytics.savedPerPeriod) { period in
                    BarMark(
                        x: .value("Period", period.periodStart),
                        y: .value("Saved", period.count))
                    .foregroundStyle(AppColors.scoopPurple)
                }
                .frame(height: 200)
            }
        }
        .padding(16)
        .background(AppColors.cardBackground)
        .cornerRadius(12)
    }

    private func breakdownSection(title: String, items: [(String, String, Int)]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(AppColors.primaryText)

            if items.isEmpty {
                Text("No data yet")
                    .font(.system(size: 13))
                    .foregroundColor(AppColors.secondaryText)
            } else {
                ForEach(items, id: \.0) { _, label, count in
                    HStack {
                        Text(label)
                            .font(.system(size: 14))
                            .foregroundColor(AppColors.primaryText)
                        Spacer()
                        Text("\(count)")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(AppColors.secondaryText)
                    }
                    .padding(.vertical, 6)
                }
            }
        }
        .padding(16)
        .background(AppColors.cardBackground)
        .cornerRadius(12)
    }
}
```

In `ProfileScreen.swift`, add the state flag near the other `@State` booleans (e.g. beside `showSubscription`):

```swift
    @State private var showInsights = false
```

Add the Insights row immediately above the Subscription `ProfileActionRow` (inside the same `VStack(spacing: 0)` action-rows block):

```swift
                // Insights row
                ProfileActionRow(
                    icon: "chart.bar.fill",
                    title: "Insights",
                    subtitle: "Your activity & streaks",
                    iconColor: AppColors.scoopPurple
                ) {
                    showInsights = true
                }
```

Add the presenting sheet immediately above the existing `.sheet(isPresented: $showSubscription)` modifier:

```swift
        .sheet(isPresented: $showInsights) {
            InsightsScreen()
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `xcodebuild test -scheme Scoop -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:ScoopTests/InsightsScreenSmokeTests`
Expected: PASS — screen builds; the full app build also compiles `ProfileScreen` with the new row.

- [ ] **Step 5: Commit**

```bash
git add Scoop/views/Insights/InsightsScreen.swift \
        Scoop/views/Profile/ProfileScreen.swift \
        ScoopTests/InsightsScreenSmokeTests.swift
git commit -m "feat(analytics): add InsightsScreen (Swift Charts) and ProfileScreen entry point (iOS)"
```

---

## Self-Review

### Spec coverage (each design-spec section → task)

| Spec section | Requirement | Covered by |
| --- | --- | --- |
| §3 / §4 totals | count + `totalDurationSeconds` ("Content saved"), no "time saved" | Task 1 (DTO), Task 3 (`countSavesInRange`, `sumDurationInRange`), Task 7 (assembly), Task 13 (`formattedDuration`, "Content saved" labels) |
| §4 saved-per-period | WEEK/MONTH time series | Task 4 (`savedPerPeriod` native query), Task 7 (granularity selection), Task 13 (`BarMark` chart) |
| §4 / §6.3 by category | group-by category, null → Uncategorised | Task 3 (`countByCategoryInRange`), Task 7 (Uncategorised mapping), Task 13 (list) |
| §4 / §6.3 by platform | group-by platform, null → Unknown | Task 3 (`countByPlatformInRange`), Task 7 (Unknown sentinel), Task 13 (list) |
| §4 / §6.3 / §11.5 top creators | group by `accountId`+`account`, display `account`, top N | Task 3 (`countByCreatorInRange`), Task 7 (top-10 truncation, Unknown fallback), Task 13 (list) |
| §5.1 compute-on-read | no stats table | Tasks 3, 4, 7 (all queries computed on read) |
| §5.2 single endpoint | one composite endpoint | Task 1 (composite DTO), Task 8 (single `GET /api/v1/analytics`) |
| §5.3 content-saved framing | factual total only | Task 1 (DTO Javadoc), Task 13 (labels: "Content saved"/"Total length") |
| §5.5 / §11.4 streaks | client tz, fallback UTC, echo zone, current+longest, all-time | Task 4 (`distinctSaveDays` in tz), Task 5 (interface `tz`), Task 6 (streak tests), Task 7 (`resolveZone`, `computeCurrentStreak`/`computeLongestStreak`) |
| §6.1 components | interface / impl / DTO / controller | Tasks 1, 5, 7, 8 |
| §6.2 DTO shape | nested records + enum | Task 1 |
| §6.3 queries | JPQL aggregates + native date_trunc + distinct days | Tasks 2, 3, 4 |
| §6.4 / §11.6 index | optional `(user_id, created_at)` V29 if seq scans | Task 11 (OPTIONAL) |
| §7 zero-user / nulls | all-zero/empty, null category/platform/account sentinels | Task 6 (`zeroUser_*`, null-bucket tests), Task 7, Task 9 (decode null tests) |
| §7 default range | last 12 months when range absent | Task 6 (`noRangeProvided_defaultsToLast12Months`), Task 7 |
| §8.1 charts | Swift Charts | Task 13 (`import Charts`, `BarMark`) |
| §8.2 iOS files | response / domain / mapper / service / view model | Tasks 9, 10, 12 |
| §8.3 navigation | Insights row in ProfileScreen via sheet | Task 13 |
| §8.4 states | loading / empty / error | Task 12 (states), Task 13 (rendering) |
| §9 testing | service unit, repo Testcontainers, controller, iOS decode/VM/chart-smoke | Tasks 3, 4, 6, 8, 9, 10, 12, 13 |
| §10 phase 1 default range 12 months | range default | Task 7 |
| §10 / §3 out-of-scope topics | Phase 2 (not built) | Intentionally omitted (out of scope) |

### Placeholder scan

No `TBD`, `TODO`, `FIXME`, `...`-elision, or "add error handling later" placeholders appear in
any implementation block. Every code snippet is complete and compilable: the service resolves
timezones and computes streaks with full method bodies; the controller has a complete
`requireUser`; the iOS service builds the full query and decodes; the view model handles all four
states; the screen renders all states with no stubbed branches.

### Type & method consistency check

- **`AnalyticsDto`** + nested `SavedPerPeriod(periodStart, count)`, `CategoryCount(categoryId, name, count)`, `PlatformCount(platform, count)`, `CreatorCount(account, accountId, count)`, and enum `PeriodGranularity { WEEK, MONTH }` — defined in Task 1 and used identically in Tasks 7 (assembly), 8 (controller test). ✓
- **`AnalyticsProjections`** value types `CategoryCountValue`/`PlatformCountValue`/`CreatorCountValue`/`PeriodCountValue` and interfaces `CategoryCount`/`PlatformCount`/`CreatorCount`/`PeriodCount` with getters `getCategoryId/getName/getPlatform/getAccount/getAccountId/getPeriodStart/getCount` — defined in Task 2, consumed verbatim in Task 6 stubs and Task 7 mapping. ✓
- **Repository methods** `countSavesInRange`, `sumDurationInRange`, `countByCategoryInRange`, `countByPlatformInRange`, `countByCreatorInRange` (Task 3) and `savedPerPeriod(userId, granularity, tz, from, to)`, `distinctSaveDays(userId, tz)` (Task 4) — called with matching signatures in Task 6 mocks and Task 7 impl. ✓
- **`AnalyticsService.getUserAnalytics(UUID, String tz, Instant from, Instant to)`** — declared in Task 5, implemented in Task 7, invoked by the controller in Task 8. (Extends spec §6.1 with the `tz` string per §5.5; documented in Task 5.) ✓
- **iOS** `AnalyticsResponse` (Task 9) → `Analytics` (Task 9) via `toAnalytics`; `AnalyticsService.getAnalytics(from:to:)` (Task 10) returns `Analytics`; `AnalyticsViewModel` + `AnalyticsLoadingState` (Task 12) consumed by `InsightsScreen(viewModel:)` (Task 13). The iOS `granularity` enum cases `.week`/`.month` decode from backend `"WEEK"`/`"MONTH"`. ✓
- **Granularity string contract:** backend native query expects `"week"`/`"month"` (Task 4); service passes lowercase `granularityUnit` (Task 7); DTO/iOS use uppercase enum names. Consistent across the boundary. ✓

