# Design Spec: Personal Analytics Dashboard ("Your Insights")

- **Status:** Draft
- **Date:** 2026-06-26
- **Repos:** `content-categorise` (backend), `TranscribeAssistant-ios/Scoop` (iOS)
- **Related specs:** [`2026-06-26-auto-tagging-entity-extraction-design.md`](./2026-06-26-auto-tagging-entity-extraction-design.md) (topics dependency for Phase 2)

> **Stacked PR plan (3 features, merge bottom→top).** Migrations are sequenced across the stack so each branch applies cleanly on the one below it. Phase 1 of this feature needs **no migration**; the optional `(user_id, created_at)` index takes the next free slot **V29**:
> 1. Collections / playlists — **V28** (stack base)
> 2. **Personal analytics** — *this spec* — Phase 1 no migration; optional index **V29**
> 3. Job-completion notifications (no migration, stack top)
>
> _Auto-tagging & search overhaul (the source of the Phase 2 topics dependency) is deferred to its own separate stack; Phase 2 of this spec waits on it._

---

## 1. Goal

Give each user a personal analytics dashboard that surfaces insights about the content
they have saved: how much they've saved over time, how it breaks down by category,
which creators/accounts and platforms they save from most, an estimated "time saved"
(sum of source video durations), and activity streaks. Topics are a stretch goal
deferred to Phase 2.

## 2. Motivation

- **Engagement & retention:** A dashboard gives users a reason to return between saves
  and reinforces the habit loop.
- **Surfacing library value:** Most of a user's saved value is invisible once it scrolls
  off the list. Aggregates ("you've saved 142 videos / 19 hours of content across 6
  categories") make the accumulated library tangible.
- **Self-knowledge:** Breakdowns by category/creator/platform help users understand and
  curate their own interests.

## 3. Scope

**In scope (Phase 1):** totals (count + content saved duration), saved-per-period time series,
breakdown by category, by platform, top creators/accounts, current + longest streak,
a single read-only aggregate endpoint, and an iOS Insights screen with charts.

**Out of scope (Phase 1):** topics/tag breakdowns (Phase 2, depends on auto-tagging),
precomputed/materialized stats, cross-user/leaderboard analytics, exporting, and any
write operations.

---

## 4. Metrics Catalogue

| Metric | Definition | Source field(s) | Query approach | Phase |
|---|---|---|---|---|
| Total transcripts | Count of distinct `user_transcripts` rows for the user | `user_transcripts.id` | JPQL `COUNT` (or reuse `countByUserId`) | v1 |
| Content saved (seconds) | `SUM` of source video duration over the user's saved videos (factual total length; **not** framed as "time saved") | `base_transcripts.duration` (Double, seconds) | JPQL `SUM(bt.duration)` with null/`<=0` guard | v1 |
| Saved per period | Count of saves bucketed by week or month of `created_at` | `user_transcripts.created_at` | Native SQL `date_trunc('week'\|'month', created_at)` + `GROUP BY` | v1 |
| By category | Count of saves grouped by category | `user_transcripts.category_id` -> `category.name` | JPQL `GROUP BY ut.category.id` | v1 |
| By platform | Count of saves grouped by platform | `base_transcripts.platform` (V21) | JPQL `GROUP BY bt.platform` | v1 |
| Top creators | Count of saves grouped by creator/account, ordered desc, capped | `base_transcripts.account` (+ `account_id`) | JPQL `GROUP BY bt.account` + limit in service | v1 |
| Current streak | Consecutive days up to today, each with >=1 save, by `created_at` (UTC) | `user_transcripts.created_at` | Fetch distinct save-dates, compute in service | v1 |
| Longest streak | Longest run of consecutive save-days ever | `user_transcripts.created_at` | Same distinct-dates set, compute in service | v1 |
| Top topics | Count of saves grouped by extracted topic/tag | `tags`/`entities` column (does not exist yet) | Depends on auto-tagging feature | Phase 2 |

**Notes**

- `duration` exists since V1/V6 (`base_transcripts.duration DOUBLE PRECISION`), populated
  from yt-dlp `VideoMetadata.duration` and validated `> 0` in `VideoService`. So "time
  saved" is computable today with no schema change.
- The existing filtered list query date-ranges on `base_transcripts.uploaded_at`, but the
  analytics "saved over time" / streak metrics intentionally use
  `user_transcripts.created_at` (when the user saved it), which is the engagement-relevant
  timestamp.

---

## 5. Design Decisions (explicit)

### 5.1 Compute-on-read vs precomputed stats table — **RECOMMEND: compute-on-read (v1)**

Mirror `UsageServiceImpl`, which derives counts at runtime rather than reading a stats
table. Per-user data volumes are small (one row per saved video per user) and the
aggregates are simple `GROUP BY`/`SUM`/`date_trunc` queries backed by the existing
`user_id` index.

**Revisit when:** per-user transcript counts grow large (tens of thousands), the
dashboard becomes a hot path, or we add expensive Phase-2 topic aggregations. At that
point consider a cached `analytics_snapshot` (e.g. nightly job or write-through cache) or
a materialized view. Explicitly **not** doing this in v1.

### 5.2 Single aggregate endpoint vs multiple — **RECOMMEND: single composite endpoint**

`GET /api/v1/analytics` returns one composite `AnalyticsDto` (totals + breakdowns + time
series). Rationale:

- The dashboard renders everything at once; one round-trip minimises latency and avoids
  N spinners on a single screen.
- The underlying queries all hinge on the same `(user_id, date-range)` filter; computing
  them together keeps the user-resolution and range-validation logic in one place.
- It mirrors `GET /api/subscription/usage` returning a single composite `UsageInfoDto`.

Trade-off vs multiple granular endpoints (`/analytics/by-category`, `/analytics/streaks`,
...): granular endpoints allow independent caching and partial refresh, but add round-trips
and client orchestration for no v1 benefit. If a future widget needs only one slice, a
granular endpoint can be added without breaking the composite one.

**Date range:** optional `from`/`to` ISO-8601 query params (mirroring `TranscriptController`'s
`@DateTimeFormat(iso = DATE_TIME) Instant`). Default = last 12 months ending now. The
time-series buckets switch from `week` to `month` automatically for long ranges (see 6.3).

### 5.3 "Content saved" duration — **factual total only, no "time saved" claim (DECIDED)**

- **Decision: do NOT ship a "time saved" metric.** It is a BS/unverifiable claim — the user
  didn't avoid watching, they read transcripts (which also takes time). We surface only the
  **factual** number: total source-video length in their library.
- Definition: `totalDurationSeconds = SUM(duration)` over the **distinct saved videos** in the
  user's library (within the selected range), presented in the UI as hours + minutes.
- **Label: "Content saved: 19h 12m"** (or similar). Never "time you saved" / "you saved Xh".
- No estimated watch/skim/read-time figure in v1 **or later** unless a real watched signal
  exists — we don't invent metrics.

### 5.4 Topics (stretch) — **defer to Phase 2 (depends on auto-tagging)**

Topics currently only live inside `base_transcripts.structured_content` (JSONB) and are
not a first-class, queryable column. Parsing JSONB per row for aggregation is fragile and
slow. Phase 2 will consume the `tags`/`entities`/topics column introduced by the
[Auto-Tagging / Entity-Extraction feature](./2026-06-26-auto-tagging-entity-extraction-design.md)
and add a `topTopics` breakdown. For v1 we ship category/creator/platform breakdowns only.

### 5.5 Streaks — **client timezone from day 1 (DECIDED)**

- A **save-day** is a calendar day **in the user's local timezone** on which the user saved
  >=1 transcript.
- **Current streak** = number of consecutive save-days ending today (or yesterday, if the
  user hasn't saved yet today — we don't reset a streak to 0 until a full day is missed).
- **Longest streak** = the longest run of consecutive save-days in the user's history.
- **Timezone (DECIDED — not UTC):** the client passes its **IANA zone** (e.g.
  `America/New_York`) as a query param `?tz=` on `GET /api/v1/analytics`. All day-bucketing
  for streaks (and the saved-per-period series) truncates in that zone, so "today" matches
  what the user sees on their device. This avoids the UTC-boundary bug where an 11pm-local
  save lands on the wrong day and silently breaks a streak.
  - **Validation/default:** parse via `ZoneId.of(tz)`; on missing/invalid value fall back to
    **UTC** and proceed (don't 400 — a bad tz shouldn't break the whole dashboard). The DTO
    echoes the `timezone` actually used so the client can confirm.
  - **Implementation:** stored timestamps stay UTC; bucketing uses
    `date_trunc('day', created_at AT TIME ZONE :tz)` in SQL, and `LocalDate.ofInstant(instant, zoneId)`
    in the in-service streak walk. No schema change, no per-user stored zone.

---

## 6. Backend Architecture

Mirrors the usage-stats pattern (`UsageService` -> `UsageServiceImpl` -> `UsageInfoDto`
-> `SubscriptionController`), keeping clean layered architecture and constructor injection.

### 6.1 Components

- **`domain/service/AnalyticsService.java`** (interface):
  ```java
  public interface AnalyticsService {
      AnalyticsDto getUserAnalytics(UUID userId, Instant from, Instant to);
  }
  ```
- **`application/internal/AnalyticsServiceImpl.java`** (`@Service`, compute-on-read):
  resolves the default range, calls repository aggregate methods, computes streaks in-service,
  assembles the `AnalyticsDto`.
- **`api/dto/AnalyticsDto.java`** (composite record, see 6.2).
- **`api/controller/AnalyticsController.java`** — **new controller** (recommended over
  extending `TranscriptController`) at `@RequestMapping("/api/v1/analytics")`.
  - Justification: analytics is read-only and cross-cutting (it spans transcripts,
    categories, base-transcript metadata) and does not belong to the transcript-CRUD
    surface; a dedicated controller keeps responsibilities separated and gives analytics
    its own `/api/v1` versioned namespace without entangling the existing
    `TranscriptController` mappings/tests.
  - userId resolution: reuse the established pattern — `@AuthenticationPrincipal UserPrincipal`
    (as in `SubscriptionController`) or a private `requireUser(Authentication)` helper copied
    from `TranscriptController`. Recommend `@AuthenticationPrincipal UserPrincipal principal`
    + `principal.getId()` for the cleanest controller signature.

Illustrative controller (snippet, not full impl):
```java
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    // constructor injection

    @GetMapping
    public ResponseEntity<AnalyticsDto> getAnalytics(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(analyticsService.getUserAnalytics(principal.getId(), from, to));
    }
}
```

### 6.2 `AnalyticsDto` shape (illustrative nested records)

```java
public record AnalyticsDto(
        long totalTranscripts,
        double totalDurationSeconds,      // "Content saved" — factual total source-video length; NOT "time saved"
        int currentStreakDays,
        int longestStreakDays,
        Instant rangeFrom,
        Instant rangeTo,
        String timezone,                  // IANA zone actually used for day-bucketing (echo of ?tz=, or "UTC" fallback)
        PeriodGranularity granularity,    // WEEK or MONTH
        List<SavedPerPeriod> savedPerPeriod,
        List<CategoryCount> byCategory,
        List<PlatformCount> byPlatform,
        List<CreatorCount> topCreators
) {
    public record SavedPerPeriod(Instant periodStart, long count) {}
    public record CategoryCount(UUID categoryId, String name, long count) {}
    public record PlatformCount(String platform, long count) {}
    public record CreatorCount(String account, String accountId, long count) {}
    public enum PeriodGranularity { WEEK, MONTH }
}
```

Notes: uncategorised saves (`category_id IS NULL`) surface as a `CategoryCount` with
`categoryId = null` and `name = "Uncategorised"`. Unknown platform/account values map to a
sentinel label rather than being dropped (decided in 7).

### 6.3 Queries

**By category (JPQL, group-by + count):**
```java
@Query("SELECT new ...CategoryCountProjection(ut.category.id, ut.category.name, COUNT(ut)) " +
       "FROM UserTranscriptEntity ut " +
       "WHERE ut.userId = :userId AND ut.createdAt BETWEEN :from AND :to " +
       "GROUP BY ut.category.id, ut.category.name ORDER BY COUNT(ut) DESC")
List<CategoryCountProjection> countByCategory(@Param("userId") UUID userId,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to);
```

**Time saved (JPQL SUM with null/`<=0` guard):**
```java
@Query("SELECT COALESCE(SUM(bt.duration), 0) FROM UserTranscriptEntity ut " +
       "JOIN ut.baseTranscript bt " +
       "WHERE ut.userId = :userId AND ut.createdAt BETWEEN :from AND :to " +
       "AND bt.duration IS NOT NULL AND bt.duration > 0")
double sumDuration(@Param("userId") UUID userId,
                   @Param("from") Instant from, @Param("to") Instant to);
```
This `duration IS NOT NULL AND duration > 0` guard is consistent with the existing list
query, which already excludes rows with null/`<=0` duration. (`COUNT` totals deliberately
do **not** apply the duration filter, so a saved video missing a duration still counts as
saved — only its seconds are excluded from "time saved".)

**Saved-per-period time series (native SQL, `date_trunc`):**
```sql
SELECT date_trunc(:granularity, ut.created_at AT TIME ZONE :tz) AS period_start, COUNT(*) AS cnt
FROM user_transcripts ut
WHERE ut.user_id = :userId AND ut.created_at BETWEEN :from AND :to
GROUP BY period_start
ORDER BY period_start;
```
`:granularity` is `'week'` or `'month'`, chosen by the service from the range length
(`<= ~3 months` -> week, else month). The service zero-fills missing buckets so the chart
shows gaps as zero rather than skipping periods. **Series length is capped** (e.g. max 53
weekly or 24 monthly buckets) to bound payload size; granularity is downgraded to keep
under the cap.

**By platform / top creators:** analogous JPQL `GROUP BY bt.platform` and `GROUP BY
bt.account, bt.account_id`; `topCreators` is sorted desc and truncated to N (e.g. 10) in
the service rather than via `LIMIT`, since the projection list is already small per user.

**Streak computation (in-service — recommended):**
1. Repository returns the user's distinct save-days **in the requested zone**:
   ```sql
   SELECT DISTINCT date_trunc('day', created_at AT TIME ZONE :tz) AS day
   FROM user_transcripts WHERE user_id = :userId ORDER BY day;
   ```
   (Streaks use the full history, independent of the dashboard `from`/`to` range. `:tz` is the
   resolved IANA zone from §5.5; "today"/"yesterday" are also computed in that zone.)
2. Service walks the ascending list of `LocalDate`s **in the resolved zone**, tracking the
   longest run of consecutive days and the run ending today/yesterday for the current streak.
   - **Recommended over a SQL window-function approach** at v1 volumes: the in-service
     version is trivially unit-testable, has no DB-dialect coupling, and the distinct-days
     set per user is tiny. Revisit with a `gaps-and-islands` window query only if the
     distinct-days set ever becomes large.

### 6.4 Performance & indexes

- **Confirmed in migrations:** `user_transcripts` has single-column indexes on `user_id`
  (`idx_user_transcripts_user_id`, V6), `base_transcript_id` (V6), `category_id` (V6), and
  `user_subcategory_id` (V24). There is **NO composite index on `(user_id, created_at)`**,
  and `created_at` is not indexed at all.
- All analytics queries filter by `user_id` and most also range/bucket on `created_at`.
  The existing `user_id` index covers the user filter; for typical per-user volumes this is
  adequate for v1.
- **Recommendation:** add a composite index `idx_user_transcripts_user_created_at ON
  user_transcripts(user_id, created_at)` to make the range scans and `date_trunc` grouping
  index-friendly. Since no such index exists today, this **would be a new migration**.
  This spec is **stack position 2** (above collections `V28`), so the
  optional index migration takes the next free slot, **`V29__add_user_transcripts_user_created_at_index.sql`**.
  Treat as a recommended-but-optional v1 optimisation; ship compute-on-read first and add the
  index if query plans show seq scans.
- Aggregates return small, bounded result sets — no pagination needed — but the time series
  is explicitly capped (6.3).

---

## 7. Error Handling & Edge Cases

- **Zero transcripts (new user):** all counts `0`, `totalDurationSeconds` `0`, streaks `0`,
  empty lists. No division anywhere in v1, so no divide-by-zero; iOS shows an empty state.
- **Saves lacking duration:** counted in totals but excluded from `timeSavedSeconds` via the
  `> 0` guard (consistent with the list query). `COALESCE(SUM, 0)` avoids null.
- **Null category:** grouped under an "Uncategorised" bucket (`categoryId = null`).
- **Null/empty platform or account:** mapped to a sentinel label (e.g. "Unknown") rather
  than dropped, so totals across buckets reconcile with `totalTranscripts`.
- **Very large libraries:** aggregates are bounded; the time series is capped and granularity
  auto-downgrades.
- **Timezone caveat:** streak day-boundaries computed in UTC (see 5.5) — documented limitation.
- **Date-range validation:** if both provided, require `from <= to` (else 400). If only one
  provided, fill the other from the default window. Reject ranges absurdly large only insofar
  as the series cap protects payload size.

---

## 8. iOS Changes (Scoop)

### 8.1 Charting library

No charting library is present today (no `import Charts`). **Recommend Apple's Swift Charts**
(`import Charts`, iOS 16+, first-party — no SPM/CocoaPods entry needed). Confirm the app's
minimum deployment target is iOS 16+ (open question 9.2).

### 8.2 New files (mirroring the Subscription/Usage pattern)

- **`models/api/AnalyticsResponse.swift`** — `Decodable` mirroring `AnalyticsDto` (nested
  `Decodable` structs for periods/category/platform/creator), like `UsageInfoResponse`.
- **`models/domain/Analytics.swift`** — domain struct(s) the UI binds to, like `UsageInfo`.
- **mapper** — `AnalyticsResponse -> Analytics` (small `toAnalytics` computed property/func,
  mirroring how `getUsageInfo()` maps response -> `UsageInfo`).
- **`service/AnalyticsService.swift`** — mirror `SubscriptionService.getUsageInfo()`:
  ```swift
  class AnalyticsService {
      static var client: HTTPClient!
      static func configure(client: HTTPClient) { self.client = client }

      static func getAnalytics(from: Date? = nil, to: Date? = nil) async throws -> Analytics {
          guard let client = Self.client else { throw NetworkError.requestFailed }
          // build "/api/v1/analytics?from=...&to=..." with ISO-8601 query items
          let response: AnalyticsResponse = try await client.request(path: path, method: .get)
          return response.toAnalytics
      }
  }
  ```
- **`viewModel/AnalyticsViewModel.swift`** — `@MainActor`, `@Published var analytics`,
  `@Published var loadingState` (loading/loaded/empty/error); `load()` calls the service.
- **Insights screen** — `views/Insights/InsightsScreen.swift` with:
  - Big-number cards: total saved, time saved (formatted "Xh Ym"), current streak.
  - Bar chart: saved-per-period (month/week) via Swift Charts `BarMark`.
  - Category breakdown: pie or horizontal bar chart.
  - Platform breakdown: small bar/segmented summary.
  - Top creators: a simple ranked list (rows), reusing existing row/list styling.

### 8.3 Navigation entry point — **recommend an "Insights" row in ProfileScreen**

`ProfileScreen` is presented as a `.sheet()` and uses `ProfileActionRow` items that toggle
`@State` booleans (e.g. `showSubscription`). Add an **Insights** `ProfileActionRow`
(icon `chart.bar.fill`) above Subscription that sets `showInsights = true` and presents
`InsightsScreen` via `.sheet`/`.fullScreenCover`, matching the existing pattern (do **not**
introduce `NavigationLink` here, since this screen is sheet-based). `DashboardScreen` /
`ActivityScreen` remain unchanged in v1; a future iteration could embed a compact insights
card on the dashboard.

### 8.4 States

- **Loading:** skeleton/spinner while `load()` runs.
- **Empty (0 transcripts):** friendly empty state ("Save your first video to unlock
  insights") reusing the `ActivityEmptyState` style.
- **Error:** retry affordance, mirroring existing service error handling.

---

## 9. Testing Strategy

- **Service unit tests (`AnalyticsServiceImplTest`)** — pure logic, nested by method-under-test
  one layer deep (per AGENTS.md):
  - streak logic: empty, single day, contiguous run, gap-broken run, current-streak ending
    today vs yesterday, longest > current, UTC boundary case.
  - assembly: default-range resolution, granularity selection + series cap, zero-fill,
    uncategorised/unknown bucketing.
- **Repository Testcontainers tests (`UserTranscriptRepositoryTest`, existing PostgreSQL
  Testcontainers)** — group-by-category, `SUM(duration)` with null/`<=0` rows excluded,
  `date_trunc` weekly/monthly bucketing, distinct save-days query.
- **Controller test (`AnalyticsControllerTest`)** — mirror existing controller test style:
  auth required, userId resolved from principal, `from`/`to` parsing + `from > to` 400,
  happy-path JSON shape.
- **iOS** — `AnalyticsResponse` decoding test (incl. nulls/empties), `AnalyticsViewModel`
  state transitions (loading -> loaded/empty/error) with a mocked `HTTPClient`, and a chart
  smoke test that the Insights view builds with representative data.

---

## 10. Phased Rollout

- **Phase 1 (this spec):** `AnalyticsService`/`Impl`/`AnalyticsDto`, `GET /api/v1/analytics`
  (totals, content-saved duration, saved-per-period series, by-category/platform/creator,
  current + longest streak in **client timezone**), default range **last 12 months**,
  compute-on-read, iOS Insights screen + Swift Charts (iOS 16+; app targets 18.x) + Profile entry.
  Optional `V29` composite index if plans show seq scans.
- **Phase 2:** `topTopics` breakdown consuming the auto-tagging `tags`/`entities` column;
  optional precomputed/cached snapshot if the endpoint becomes hot or volumes grow.

---

## 11. Open Questions / Assumptions

1. ~~**Time-saved framing**~~ **RESOLVED (see §5.3):** NO "time saved" metric — ship factual
   "Content saved (Xh Ym)" only; never an invented skim/read-time figure.
2. ~~**iOS min deployment target**~~ **RESOLVED:** app targets **iOS 18.0/18.5** (confirmed in
   project settings), comfortably above Swift Charts' iOS 16+ requirement. Use Swift Charts.
3. ~~**Default range**~~ **RESOLVED:** default **last 12 months** (streaks remain all-time).
4. ~~**Streak timezone**~~ **RESOLVED (see §5.5):** client tz from day 1 via `?tz=` IANA zone;
   invalid/missing falls back to UTC; DTO echoes the `timezone` used. Saved-per-period series
   also buckets in this zone for consistency.
5. ~~**Creator identity**~~ **RESOLVED:** group by `account_id` when present (stable), display
   `account` label; fall back to `account` when `account_id` is null.
6. ~~**Composite index**~~ **RESOLVED:** ship compute-on-read **without** the index; add
   `V29 (user_id, created_at)` only if query plans show seq scans.
