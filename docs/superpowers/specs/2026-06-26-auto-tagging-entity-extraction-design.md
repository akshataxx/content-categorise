# Design Spec: Auto-Tagging & Entity Extraction for Richer Filtering

**Status:** Draft
**Author:** Rovo Dev
**Date:** 2026-06-26
**Repos affected:** Backend (`content-categorise`), iOS (`Scoop`)

> **⚠️ Deferred — separate stack.** This feature has been split out of the 3-PR feature stack
> (Collections → Analytics → Notifications) and will be implemented **afterwards as its own stack**,
> because it is really a **search-architecture overhaul**, not a standalone tagging add-on. See
> §2 / the search design discussion: tagging is the deterministic **Filtering** stage of a proper
> search pipeline (query-understanding → retrieval → RRF fusion → filtering → snippets), and should
> be designed alongside ranking (RRF) and presentation (snippets) changes — not bolted onto the
> current hand-tuned hybrid scorer.
>
> **Migration numbering:** because the other three PRs land first, the latest migration at the time
> this work starts will be **V29** (collections=V28, analytics optional index=V29). This feature's
> migration(s) therefore start at **V30+** — the `V28` references below are historical and must be
> rebased to the next free version when this stack begins.

---

## 1. Title & Goal

Add **auto-tagging** and **entity extraction** to the transcript pipeline so that
every transcript carries a normalized set of `tags` (e.g. `vegetarian`, `quick`,
`budget`) and structured `entities` (people / products / places / topics). These
become **first-class, queryable data** that the API returns, the iOS app displays
as chips, and both layers can use for **deterministic, exact filtering** and tag
browsing/faceting.

**Goal:** Turn the LLM-derived signal the pipeline already discards (the GENERAL
prompt's `tags` array) plus net-new entity extraction into a durable, indexed,
filterable feature across backend and iOS.

---

## 2. Motivation / Why not just search?

The app already has a strong search stack:

- **Vector search** — `embedding vector(1536)` + HNSW index (migration V25),
  queried via cosine distance in `searchByEmbedding`
  (`UserTranscriptRepositoryImpl`).
- **Full-text search** — a GIN `to_tsvector` index over
  title/generated_title/description/structured_content/transcript (migration V26),
  blended with the vector score and an `ILIKE` exact-match boost in the same
  hybrid query.

That stack is **fuzzy and probabilistic** by design. It answers *"what is roughly
about X?"* and ranks results. It does **not** give you:

1. **Tags as structured data returned & displayed.** The GENERAL prompt already
   emits a `tags` array (`OpenAIClientImpl.buildGeneralPrompt`, line ~413) and
   several category prompts emit their own `tags` (e.g. cooking line ~221, beauty
   line ~246). **Nothing parses, stores, displays, or filters on any of them
   today** — they are buried inside the `structured_content` JSONB blob and only
   reach search as undifferentiated text inside the `::text` cast.
2. **Deterministic exact filtering.** "Show me everything tagged `vegetarian`"
   should be an exact set operation with stable, repeatable results — not a
   ranked similarity guess that may drop borderline matches or include
   near-misses.
3. **Genuinely-new entity extraction.** People, products, places and topics are
   not extracted as discrete fields today. Extracting them unlocks faceting like
   "all videos mentioning *Cerave*" or "all travel videos about *Tokyo*".
4. **Tag browsing / faceting.** A finite, deduped tag vocabulary can power chip
   strips, "popular tags", and combinable filters — UX that free-text search
   cannot provide.

In short: **search finds; tagging organizes.** This feature is complementary to
search, not a replacement, and it also *improves* search by feeding clean tags
and entities into the embedding input.

---

## 3. Architecture & Data Flow

The extraction point already exists; we extend it rather than add a new stage.

```
video → download → Whisper transcript → categorise (category name)
      → OpenAIClient.extractStructuredContent(...)   ← prompts now ALSO emit
                                                         tags[] + entities{}
      → VideoService persists structuredContent (unchanged, String/JSONB)
                +  NEW: parse → normalize → persist tags text[] + entities jsonb
      → generateAndStoreEmbedding (embedding input already references
                                   "tags, topics, entities"; now backed by data)
      → buildResponse DTO (adds tags + entities)
```

Key boundaries:

- **Extraction (LLM):** `OpenAIClientImpl` prompt builders emit a consistent
  `tags`/`entities` block inside the same JSON object they already return. No new
  OpenAI call.
- **Normalization (domain boundary):** `VideoService`, after calling
  `extractStructuredContent`, parses the returned JSON, normalizes tags/entities,
  and writes them to the new dedicated columns. This keeps the LLM client dumb
  (it only builds prompts / returns strings) and centralizes normalization where
  persistence happens — consistent with the existing clean-layered conventions.
- **Storage:** dedicated indexed columns on `base_transcripts` (see §4, Option B).
- **Query:** new repository predicate using array overlap for fast, exact tag
  filtering.

---

## 4. Storage Approach (Decision)

### Option A — Keep tags/entities inside `structured_content` JSONB only

- **Pros:** Zero migration. Prompt changes alone surface the data; the DTO could
  expose a parsed sub-object.
- **Cons:** No efficient **exact** filtering. Filtering on a tag means either
  `structured_content -> 'tags' ? :tag` (JSONB containment, but no dedicated GIN
  index tuned for it and tags are mixed in with everything else) or string
  `ILIKE` on the `::text` cast (fuzzy, false positives like matching a tag
  substring inside `keyPoints`). Entity filtering is even worse. This is the
  status quo's failure mode — tags are *present but unusable*.

### Option B — Dedicated columns + GIN indexes *(RECOMMENDED)*

Add to `base_transcripts`:

- `tags text[]` with a GIN index → fast containment/overlap (`&&`, `@>`).
- `entities jsonb` with a GIN index → fast key/containment queries on
  people/products/places/topics.

- **Pros:** Deterministic, indexed exact filtering. `tags && ARRAY[...]` (overlap
  = OR) and `tags @> ARRAY[...]` (contains = AND) are index-backed and
  unambiguous. Clean separation from the free-text `structured_content` blob.
  Entities get their own queryable structure.
- **Cons:** One migration (V28) on a populated table; columns are empty until
  backfill (see §6). Slight write-path cost (negligible).

**Recommendation: Option B.** Exact, deterministic filtering — the core value
prop over existing search — *requires* a dedicated, indexable representation.
`text[] + GIN` is the idiomatic Postgres choice for tag sets and is consistent
with the project's existing reliance on GIN/HNSW indexes for search.

> Note: `structured_content` continues to also contain the tags/entities (the LLM
> returns one JSON object). The dedicated columns are the **normalized,
> filterable projection** of that data — a deliberate, documented denormalization.

---

## 5. Backend Changes

### 5.1 Migration V28 (next version after V27)

File: `src/main/resources/db/migration/V28__add_tags_and_entities_to_base_transcripts.sql`

```sql
-- V28: Add dedicated, filterable tags + entities columns to base_transcripts.
ALTER TABLE base_transcripts
    ADD COLUMN IF NOT EXISTS tags text[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS entities jsonb;

COMMENT ON COLUMN base_transcripts.tags IS
    'Normalized (lowercased, trimmed, deduped) tag set for exact filtering/faceting.';
COMMENT ON COLUMN base_transcripts.entities IS
    'Extracted entities: {people:[],products:[],places:[],topics:[]}.';

-- Fast overlap/containment filtering on tags (&&, @>).
CREATE INDEX IF NOT EXISTS idx_base_transcripts_tags
    ON base_transcripts USING gin (tags);

-- Fast containment queries on entities.
CREATE INDEX IF NOT EXISTS idx_base_transcripts_entities
    ON base_transcripts USING gin (entities);
```

Notes:
- `NOT NULL DEFAULT '{}'` for `tags` makes the column safe on the populated table
  and avoids null-handling in predicates. `entities` is nullable (older rows have
  none until backfilled).
- Follows the Flyway naming convention `V{n}__snake_case.sql` and the
  `IF NOT EXISTS` idempotency style used in V25/V26.

### 5.2 Entity & Domain model

`data/entity/BaseTranscriptEntity.java` — add mapped fields. Use Hibernate's
array/JSON support:

```java
// tags text[]
@JdbcTypeCode(SqlTypes.ARRAY)
@Column(name = "tags", columnDefinition = "text[]")
private List<String> tags = new ArrayList<>();

// entities jsonb
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "entities", columnDefinition = "jsonb")
private Entities entities; // small nested type or Map<String,List<String>>
```

`domain/model/BaseTranscript.java` — add the same two fields as plain Java
(`List<String> tags`, and an `Entities`/`Map` for entities) with getters/setters,
keeping the domain model framework-free per the AGENTS.md separation rule. Add the
fields to the all-args constructor *or* keep them setter-populated to avoid a
breaking constructor change (recommend setter-populated, mirroring how
`generatedTitle`/`platformVideoId` are already handled as setter-only fields).

A small immutable carrier (record) for entities keeps typing clean:

```java
public record Entities(List<String> people, List<String> products,
                       List<String> places, List<String> topics) {}
```

### 5.3 OpenAIClient prompt changes (illustrative)

Standardize a shared block appended to **all ~10 builders** in
`buildStructuredContentPrompt`'s targets. Each builder keeps its category-specific
fields *and* adds the consistent tags/entities contract. Illustrative addition to
the JSON shape + rules (shown for the general builder; replicate the block in
each):

```text
  "tags": ["lowercase", "deduped", "5-15 items"],
  "entities": {
    "people":   ["named people"],
    "products": ["named products/brands"],
    "places":   ["named locations"],
    "topics":   ["key topics/themes"]
  },
  ...existing category fields...
```
Rules to add to every builder:
```text
- tags: 5-15 short lowercase keywords, no duplicates, no '#'
- entities: include only clearly-named items; use [] when none
- Return ONLY valid JSON, no explanations
```

To avoid copy-paste drift across builders, extract a private constant/string
helper (e.g. `tagsAndEntitiesContract()`) that each builder concatenates into its
JSON skeleton. This preserves per-category richness while guaranteeing a uniform
shared block. The existing `cleanJsonResponse(...)` still strips code fences.

### 5.4 VideoService persistence + normalization

In `_processVideoAndCreateTranscript` (around lines 400-411) and in
`reextractAndReembedAll` (around 595-620), after
`setStructuredContent(structuredContent)`, parse and persist normalized
tags/entities **before** save:

```java
StructuredExtraction parsed = structuredContentParser.parse(structuredContent);
baseTranscript.setTags(TagNormalizer.normalize(parsed.tags()));      // §5.4.1
baseTranscript.setEntities(TagNormalizer.normalizeEntities(parsed.entities()));
baseTranscriptRepository.save(baseTranscript);
```

A small backend-side parser (reuse Jackson `ObjectMapper`, already injected into
`OpenAIClientImpl`; expose a tiny `StructuredContentParser` helper in
`application`/`util`) reads `tags` + `entities` defensively from the JSON.

#### 5.4.1 Normalization rules (at the persistence boundary)

- **Tags:** trim → lowercase → drop blanks → strip leading `#` → dedupe
  (preserve order) → cap at **15** (`MAX_TAGS`). Empty input → `[]`.
- **Entities:** for each of people/products/places/topics: trim → dedupe
  (case-insensitive) → cap each list at **20**; original casing preserved
  (entities are proper nouns). Missing/null group → empty list. If the whole
  object is absent → store `null`.
- Centralized in a `TagNormalizer` util with unit tests (§8).

### 5.5 DTO additions

`api/dto/TranscriptDtoWithAliases.java` (a record) — append two components
(records are positional; append at the end to preserve existing field order for
any positional consumers):

```java
    List<String> tags,
    Entities entities
```

`application/mapper/VideoMapper.buildResponse(...)` — pass
`baseTranscript.getTags()` (default to `List.of()` if null) and
`baseTranscript.getEntities()` into the constructor.

### 5.6 New filter params on TranscriptController

Add a repeatable `tags` param to `GET /` and `GET /page` (mirroring the existing
`categoryIds`/`subcategoryIds` style):

```java
@RequestParam(required = false) List<String> tags,
// optional: tagMatch=any|all (default "any" = overlap)
```

Plumb through `TranscriptService.allFilteredTranscripts(...)` /
`pagedFilteredTranscripts(...)` into the repository. Default match mode = **any**
(array overlap, OR semantics); `all` = containment (AND). Normalize incoming tag
params with the same `TagNormalizer` so client casing doesn't matter.

### 5.7 Repository predicate for tag filtering

In `UserTranscriptRepositoryImpl.filterPredicates(...)` (lines ~76-112), add a
predicate against the joined `base_transcripts.tags`. Criteria API has no native
array-overlap operator, so use a Postgres function via `cb.function`/`isTrue`:

```java
if (tags != null && !tags.isEmpty()) {
    // overlap (ANY): bt.tags && :tags  → array_overlap(...) = true
    predicates.add(cb.isTrue(cb.function(
        "array_overlap", Boolean.class,
        baseTranscriptJoin.get("tags"),
        cb.literal(tags.toArray(String[]::new)))));
}
```

- For **all/containment** semantics, swap to an `array_contains`-style function
  (`bt.tags @> :tags`).
- Implementation note: register/confirm the operator mapping (a thin SQL
  function wrapping `&&` / `@>`, or use a `@Query` native method on the
  `BaseTranscript`/`UserTranscript` repository if Criteria function registration
  proves awkward). The native-query fallback mirrors the existing
  `searchByEmbedding` approach.

**Entity filtering (note, not v1 required):** entities can be filtered with a
JSONB containment predicate, e.g.
`entities -> 'products' @> '["cerave"]'::jsonb`. Recommend deferring an entity
filter *endpoint* to a follow-up; the column + GIN index land now so it's ready.
Keep entity values returned/displayed in v1.

### 5.8 Tags enrich embedding / search

`buildEmbeddingInput` (lines ~635-654) already appends a field labeled
*"Structured metadata, tags, topics, entities and key points"* sourced from
`structuredContent`. Since tags/entities remain inside `structured_content`, the
embedding already benefits. **Optionally** add an explicit field for emphasis:

```java
appendSearchField(searchDocument, "Tags",
    entity.getTags() == null ? "" : String.join(", ", entity.getTags()));
```

This is low-risk and makes the tag signal explicit/weighted in the embedding and
in the V26 full-text index (which already covers `structured_content::text`).

---

## 6. Backfill Plan

Existing transcripts will have empty `tags`/null `entities` until reprocessed.

- Reuse the existing **`POST /api/admin/reextract-and-reembed`**
  (`AdminController` → `VideoService.reextractAndReembedAll`). This already loops
  every `base_transcript`, re-extracts structured content with the *updated*
  prompts, re-saves, and re-embeds — we only add the parse→normalize→persist of
  tags/entities inside that loop (§5.4), so the **same endpoint** backfills the
  new columns with no new API surface.
- Sequence: ship V28 + entity/prompt/normalization code → deploy → call
  `POST /api/admin/reextract-and-reembed` once in prod.
- The migration default (`tags '{}'`) keeps un-backfilled rows valid and
  filter-safe (they simply never match a tag filter).
- Operation is idempotent and re-runnable; failures are per-row logged (existing
  `[reextract] failed ...` pattern) and counted.

---

## 7. iOS Changes (Scoop)

### 7.1 API model — `models/api/TranscriptResponse.swift`

Add optional fields (Codable; backward compatible — server may omit for old
rows):

```swift
let tags: [String]?
let entities: Entities?   // people/products/places/topics, all optional arrays
```

Define `Entities: Codable` (all four arrays optional) alongside.

### 7.2 Domain — `models/domain/Transcript.swift`

Add `let tags: [String]` (default `[]`) and `let entities: Entities?`; thread
through both initializers (the designated init and `init(from: TranscriptEntity)`).

### 7.3 SwiftData entity — `models/entity/TranscriptEntity.swift`

Add `var tags: [String]` (SwiftData stores `[String]` natively) and store
entities as a decoded value type or as a `Data?`/JSON string for simplicity
(recommend `var entitiesJson: String?` to avoid SwiftData nested-type friction,
decoded on read). Update the init.

### 7.4 Mapper — `models/mapper/TranscriptMapper.swift`

- `Transcript.from(_ response:)` → map `response.tags ?? []` and
  `response.entities`.
- `TranscriptEntity.from(_ model:)` and `toDomain()` → carry tags/entities
  (encode/decode `entitiesJson` if using the string approach).

### 7.5 Decode + display

- Extend `GeneralContent`/`StructuredContent.swift` is **not** required for the
  primary path because tags/entities now arrive as top-level Transcript fields.
  (Optionally also add `tags` to `GeneralContent` so the existing
  `StructuredContentParser` no longer silently drops them — low priority.)
- **Tag chips** on the transcript card and detail view: reuse the existing chip
  visual language from `SubcategoryPickerSheet` (`chipsSection`). Render
  `transcript.tags` as tappable chips; tapping a chip applies a tag filter
  (§7.6). Show entities in the detail view grouped by people/products/places/
  topics.

### 7.6 Tag filter UI — `FeedViewModel` + Feed screen

Mirror the existing client-side subcategory chip strip
(`activeSubcategoryFilter`, `subcategoryFilteredTranscripts`,
`subcategoryCountsById`):

```swift
@Published var activeTagFilters: Set<String> = []   // empty = All

private var tagFilteredTranscripts: [Transcript] {
    guard !activeTagFilters.isEmpty else { return transcripts }
    // overlap (ANY) to match backend default
    return transcripts.filter { !activeTagFilters.isDisjoint(with: Set($0.tags)) }
}
```

- For **local** filtering on the already-loaded feed, the above is enough and
  matches today's subcategory pattern.
- For **server-side** tag-filtered fetch (large libraries / faceting), add a
  `TranscriptService` call mirroring `getAllTranscripts`, hitting
  `GET /transcript?tags=a&tags=b` via the generic `HTTPClient.request`:

```swift
static func getTranscripts(tags: [String],
    completion: @escaping (Result<[TranscriptResponse], NetworkError>) -> Void) {
    // path: "/transcript?" + tags.map { "tags=\($0.percentEncoded)" }.joined(separator: "&")
    // ...same Task/HTTPError handling as getAllTranscripts...
}
```

- A tag filter strip (chip row above the feed) drives `activeTagFilters`; counts
  via a `tagCountsById`-style computed property.

---

## 8. Error Handling & Edge Cases

- **Malformed LLM JSON:** backend parser must be defensive — wrap parsing in
  try/catch, reuse `cleanJsonResponse(...)` before parsing, and on failure store
  `tags = []` / `entities = null` (never fail the whole pipeline). iOS already
  treats malformed structured content as `.unknown` (`StructuredContentParser`).
- **Empty / oversized tag lists:** `TagNormalizer` caps at 15 tags / 20 per
  entity group; empty → `[]`.
- **Null entities:** column nullable; DTO/iOS treat as no entities.
- **Migration on populated table:** safe defaults (`tags '{}'`, `entities` null);
  `IF NOT EXISTS` idempotent; no rewrite of existing data required.
- **Backward compatibility:**
  - *Old iOS clients* ignore the new JSON fields (additive, optional) — no break.
  - *New iOS, old server* — `tags`/`entities` optional in `TranscriptResponse`,
    default to `[]`/nil.
  - *DTO record* — append new components at the end to avoid disturbing existing
    positional/serialization order.
- **Tag param casing:** server normalizes incoming `tags` params so client casing
  is irrelevant; overlap match is case-consistent because stored tags are
  lowercased.

---

## 9. Testing Strategy

**Backend**
- *Prompt/contract:* unit test that each builder's output JSON skeleton contains
  the `tags`/`entities` keys (string-contains assertions on the prompt, matching
  the lightweight style usable without a live OpenAI call).
- *Normalization:* `TagNormalizer` unit tests — lowercasing, trim, dedupe,
  `#`-strip, cap at 15/20, empty/null handling, entity group defaults.
- *Repository:* a Testcontainers PostgreSQL test (follow
  `BaseTranscriptRepositoryTest`: `@DataJpaTest @Testcontainers @ActiveProfiles
  ("test")`, `postgres:15-alpine`, `@DynamicPropertySource`) that saves rows with
  known tag arrays and asserts overlap (`any`) and containment (`all`) filtering
  return the correct sets — verifying the GIN-backed predicate. *(Ensure the test
  container runs Flyway V28 / array column support.)*
- *Mapper/DTO:* `VideoMapper` test asserting tags/entities flow into
  `TranscriptDtoWithAliases`, including null→`[]` defaulting.
- *Controller:* extend `TranscriptControllerTest` for the new `tags` param
  plumbing.

**iOS**
- Decoding tests: `TranscriptResponse`/`Transcript` decode payloads with tags +
  entities, with entities present, with entities absent, and with `tags` omitted
  (→ `[]`). Mapper round-trip (API→domain→entity→domain) preserves tags.
- `tagFilteredTranscripts` logic test in `FeedViewModel` (overlap semantics,
  empty filter = all).

---

## 10. Smaller Alternative (Minimal Scope)

If minimal scope is preferred:

- **Do:** parse the GENERAL (and any existing) prompt's current `tags` array,
  expose it on the DTO + iOS, display chips, and filter exactly.
- **Storage:** Option A — filter via JSONB on `structured_content -> 'tags'`
  (e.g. `?`/`@>`), **no V28 migration, no new columns**.
- **Drops:** no entity extraction, no dedicated GIN index (filtering is slower /
  less clean on large datasets), no standardized cross-category contract (only
  categories whose prompt already emits `tags`), no entity UI.
- **What shrinks:** no migration, no entity record/DTO/iOS entity model, prompt
  changes limited to the general builder, repository uses a JSONB predicate
  instead of array overlap. Backfill still via the existing reextract endpoint.

This is a viable "phase 0" that can be upgraded to Option B later (add V28 +
backfill to populate the dedicated columns).

---

## 11. Open Questions / Assumptions

1. **Tag match default** — confirm `any` (overlap/OR) as the default vs `all`
   (AND). Spec assumes `any` with an optional `tagMatch` param.
2. **Entity filtering in v1** — assumed *display only* in v1; entity *filter
   endpoint* deferred (column + index still land now). Confirm.
3. **Global tag vocabulary / faceting endpoint** — do we want a
   `GET /transcript/tags` (distinct tags + counts for the user) to power the
   filter strip server-side? Assumed out of scope for v1 (iOS derives counts
   client-side, as it does for subcategories).
4. **Caps** — `MAX_TAGS=15`, per-entity-group cap `20`. Confirm acceptable.
5. **`category` vs `categories` table name** — the hybrid search SQL joins
   `category` (singular, per migration V1) while `VideoService.resolveCategoryName`
   queries `categories`. This is a *pre-existing* inconsistency unrelated to this
   feature; flagged only so new tag SQL consistently uses the real table name
   (`base_transcripts` for tags, so unaffected).
6. **Embedding re-emphasis** — should we add an explicit `Tags:` field to
   `buildEmbeddingInput` (§5.8) or rely on tags already being inside
   `structured_content`? Assumed: add the explicit field (low risk).
7. **iOS entities storage** — assumed `entitiesJson: String?` on the SwiftData
   entity to avoid nested-model friction; confirm preference vs a SwiftData
   nested type.
```