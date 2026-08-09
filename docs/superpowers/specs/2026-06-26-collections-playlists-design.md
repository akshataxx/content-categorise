# Collections / Playlists / Folders — Design Spec

**Date:** 2026-06-26
**Status:** Draft (awaiting user review)
**Scope:** Add user-owned, named **Collections** that group saved transcripts.
A transcript may belong to **many** collections (many-to-many). Backend
(content-categorise, Java 24 / Spring Boot 3.5) + iOS (Scoop, SwiftUI).

> **Stacked PR plan (3 features, merge bottom→top).** Migrations are sequenced across the stack so each branch applies cleanly on the one below it:
> 1. **Collections / playlists** — *this spec* — **V28** (stack base)
> 2. Personal analytics (Phase 1, no migration; optional index **V29**)
> 3. Job-completion notifications (no migration, stack top)
>
> _Auto-tagging & search overhaul is deferred to its own separate stack (see [`2026-06-26-auto-tagging-entity-extraction-design.md`](./2026-06-26-auto-tagging-entity-extraction-design.md))._

---

## 1. Goal

Let a user curate arbitrary, named groupings ("Weeknight dinners", "Marathon
prep", "Watch later") of their saved transcripts. A transcript can be filed
into any number of collections at once, independent of its AI category.

## 2. Motivation — Collections vs Categories/Subcategories

| Concept | Owner | Cardinality on a transcript | Source |
|---|---|---|---|
| **Category** (root) | shared taxonomy | exactly one | AI-assigned |
| **Subcategory** | per user | at most one (must match parent category) | user-curated, taxonomy-shaped |
| **Collection** | per user | many (0..N) | user-curated, free-form |

Categories/subcategories form a **single-assignment taxonomy** the AI
participates in. Collections are **user-curated arbitrary groupings** with no AI
involvement and no parent/child constraint — closer to playlists/folders. This
is why they need a separate many-to-many shape rather than another FK on
`user_transcripts`.

## 3. Non-goals

- AI auto-assignment of transcripts into collections.
- Sharing collections across users / public collections.
- Nested collections (collections inside collections).
- Re-using the existing `user_subcategory_id` FK (that is 1-to-many and stays as
  is).
- Collection membership for `base_transcripts` directly — collections only ever
  reference per-user `user_transcripts` rows.

## 4. Key design decisions (explicit)

### 4.1 Many-to-many modeling — RECOMMENDATION: explicit join entity

Model membership as an **explicit join table + JPA entity**
`CollectionTranscriptEntity` (`collection_id`, `user_transcript_id`,
`added_at`), **not** a JPA `@ManyToMany`.

**Why explicit:**
- Matches the codebase's explicit style (e.g. `UserTranscriptEntity` is itself a
  hand-written join over `BaseTranscriptEntity`; the repos use explicit
  ownership-scoped queries).
- Allows per-membership metadata now (`added_at`) and future fields (manual
  ordering/`position`, "added by", pin flags) without a migration to escape
  `@ManyToMany`.
- Idempotency, ownership checks, and counts are easy to express as plain
  repository queries.

**Tradeoff vs `@ManyToMany`:** `@ManyToMany` is less boilerplate for a pure link
set, but hides the join row, makes adding columns a breaking refactor, and makes
ownership-scoped + counted queries awkward. The small extra boilerplate is worth
it here.

### 4.2 What gets collected — per-user rows, double ownership check

Collections reference `user_transcripts.id` (per-user), never
`base_transcripts`. Before any link is created, **both** the collection **and**
the `user_transcript` must belong to the requesting `userId`. This mirrors the
ownership pattern already proven in
`TranscriptService.setSubcategory(...)` — it loads the transcript via
`findByIdAndUserId(...)` and the subcategory via
`getOwnedSubcategory(userId, ...)`, throwing not-found if either is not owned.

### 4.3 Cascade / cleanup — collection is the source of truth

- Delete a **collection** → its join rows cascade-delete; member transcripts are
  untouched (`ON DELETE CASCADE` on `collection_id`).
- Delete a **user_transcript** → its join rows cascade-delete; it silently
  leaves all collections (`ON DELETE CASCADE` on `user_transcript_id`).

Both FKs on the join table are `ON DELETE CASCADE`.

### 4.4 Endpoint shape — `/api/v1/collections`

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/collections` | create collection |
| GET | `/api/v1/collections` | list user's collections (each with `transcriptCount`) |
| GET | `/api/v1/collections?transcriptId={userTranscriptId}` | list collections, each with a transient `contains` flag (does this collection contain the given transcript) — see §4.5 |
| GET | `/api/v1/collections/{id}` | get one collection |
| PATCH | `/api/v1/collections/{id}` | rename / edit description |
| DELETE | `/api/v1/collections/{id}` | delete collection (cascades join rows) |
| POST | `/api/v1/collections/{id}/transcripts/{userTranscriptId}` | add transcript |
| DELETE | `/api/v1/collections/{id}/transcripts/{userTranscriptId}` | remove transcript |
| GET | `/api/v1/collections/{id}/transcripts` | list members ordered by `position` (paginated, `TranscriptPageResponse`) |
| PATCH | `/api/v1/collections/{id}/order` | reorder members (`{userTranscriptIds:[...]}` → rewrites `position`); see §4.6 |

### 4.5 Picker pre-check — relationship query, NOT membership-on-transcript (DECIDED)

The collection picker needs to show which collections already contain the transcript
being added. **Decision: collection membership does _not_ live on the transcript DTO.**
Membership is a property of the **join**, not of either entity — a transcript no more
"has collections" than a collection "is" its transcripts. Smearing `collectionIds` onto
every `TranscriptDto` would couple a user-organisation overlay to the core content object
and bloat the hot feed payload. We also do **not** add a separate `/membership` endpoint.

Instead, the **list-collections endpoint answers the relationship question about the
collections it is already returning**: when called with `?transcriptId={userTranscriptId}`,
each returned `CollectionDto` carries a **transient `contains: boolean`**. This is the
collection side legitimately answering "do I contain X?" — no transcript coupling, no new
endpoint. On the plain `GET /api/v1/collections` (no `transcriptId`), `contains` is absent/`null`.

Backend: one extra set lookup (fetch the join rows for that one `transcriptId` across the
user's collections, then mark `contains`) computed only on this query variant. The picker
fetches the user's collections once (it needs the list anyway) and renders checkmarks directly.

### 4.6 Manual ordering — `position` column (DECIDED: v1)

Members are **user-orderable** in v1 via an integer `position` on the join row.

- **Add semantics:** a new member is appended — `position = COALESCE(MAX(position), -1) + 1`
  for that collection, computed inside the same `@Transactional` add. Idempotent re-add does
  not change position.
- **Read:** `GET /collections/{id}/transcripts` orders by `position ASC` (then `added_at ASC`
  as a stable tiebreak), backed by `idx_collection_transcript_collection_position`.
- **Reorder endpoint:** `PATCH /api/v1/collections/{id}/order` with body
  `{ "userTranscriptIds": [<ordered uuids>] }`. **Partial reorder is allowed** (so a client
  can reorder within a loaded page without fetching all 5000 members): every supplied id must
  be a current member of the (owned) collection, but the array need not cover all members.
  The service assigns the supplied ids contiguous positions starting at the **minimum current
  position among them**, preserving the relative placement of untouched members around them, in
  one transaction. Returns `204`. (Rejected only if a supplied id is not a member — see §9.)
- **Concurrency:** positions are not globally unique (no unique constraint) — gaps/dupes from
  races are harmless because read order is deterministic via the `(position, added_at)` sort;
  a reorder fully rewrites them.

### 4.7 Limits — soft caps (DECIDED: v1)

Service-enforced guardrails to prevent pathological/abusive data, returning **422
Unprocessable Entity** (new `CollectionLimitExceededException` → mapped in the global handler):

- **Max collections per user:** **200**.
- **Max transcripts per collection:** **5000**.

Checked in `createCollection` and `addTranscript` respectively, before insert. These are
generous for real use; tune via constants. (Counts reuse the existing `countBy...` queries.)

---

## 5. Architecture (layered, mirrors UserSubcategory slice)

Follows the established pattern: domain model ⟷ entity (separate), `@Component`
mapper, `JpaRepository`, `@Service @Transactional`, controller with
`requireUser(...)` ownership scoping. Base package: `com.app.categorise`.

```
api/controller/CollectionController.java
api/dto/CollectionDto.java
api/dto/CreateCollectionRequest.java
api/dto/UpdateCollectionRequest.java
application/mapper/CollectionMapper.java
data/entity/CollectionEntity.java
data/entity/CollectionTranscriptEntity.java        (explicit join)
data/repository/CollectionRepository.java
data/repository/CollectionTranscriptRepository.java
domain/model/Collection.java
domain/service/CollectionService.java
exception/CollectionNotFoundException.java
src/main/resources/db/migration/V28__add_collections.sql
```

---

## 6. Database — migration `V28__add_collections.sql`

This PR is the **stack base** (position 1); the latest existing migration is `V27__unique_app_store_original_transaction.sql`, so collections uses **V28**.
Conventions taken from `V24__add_user_subcategory.sql`: `UUID PRIMARY KEY`,
`user_id ... REFERENCES users(id) ON DELETE CASCADE`, explicit `idx_*` indexes,
`uq_*` unique constraints, `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`.

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
    position INTEGER NOT NULL DEFAULT 0,  -- manual ordering within the collection (see §4.6)
    CONSTRAINT pk_collection_transcript
        PRIMARY KEY (collection_id, user_transcript_id)
);

CREATE INDEX idx_collection_transcript_collection_id
    ON collection_transcript(collection_id);
CREATE INDEX idx_collection_transcript_user_transcript_id
    ON collection_transcript(user_transcript_id);
-- Ordered member listing: collection_id + position is the read path
CREATE INDEX idx_collection_transcript_collection_position
    ON collection_transcript(collection_id, position);
```

Notes:
- The composite PK `(collection_id, user_transcript_id)` enforces "a transcript
  appears at most once per collection" at the DB level — this is what makes
  add-duplicate handling safe (see §9).
- `updated_at` is included because PATCH exists. The entity sets it via
  `@PreUpdate` (and `@PrePersist` initialises `created_at`/`updated_at`), matching
  the `@PrePersist` style on the existing entities.

---

## 7. Backend artifacts

### 7.1 `CollectionEntity`
Mirrors `UserSubcategoryEntity`: `@Id @GeneratedValue(strategy = GenerationType.UUID)`,
`user_id` column, `name`, nullable `description`, `created_at`, `updated_at`,
`@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","name"}))`,
`@PrePersist`/`@PreUpdate` timestamp hooks.

### 7.2 `CollectionTranscriptEntity` (explicit join)
Composite key via `@IdClass` or `@EmbeddedId`; `@ManyToOne` to `CollectionEntity`
(`collection_id`) and `@ManyToOne` to `UserTranscriptEntity`
(`user_transcript_id`), plus `added_at`. Illustrative:

```java
@Entity
@Table(name = "collection_transcript")
@IdClass(CollectionTranscriptId.class)
public class CollectionTranscriptEntity {
    @Id @ManyToOne @JoinColumn(name = "collection_id")
    private CollectionEntity collection;
    @Id @ManyToOne @JoinColumn(name = "user_transcript_id")
    private UserTranscriptEntity userTranscript;
    @Column(name = "added_at", nullable = false)
    private Instant addedAt; // @PrePersist sets now()
}
```

### 7.3 `Collection` domain model
Plain Java object (no JPA), like `domain/model/UserTranscript.java`:
`id, userId, name, description, createdAt, updatedAt`. Membership is *not* held
on the domain model; counts/members are returned via DTOs.

### 7.4 Repositories
`CollectionRepository extends JpaRepository<CollectionEntity, UUID>` with
ownership-scoped finders mirroring `UserSubcategoryRepository`:

```java
List<CollectionEntity> findByUserId(UUID userId);
Optional<CollectionEntity> findByIdAndUserId(UUID id, UUID userId);
Optional<CollectionEntity> findByUserIdAndNameIgnoreCase(UUID userId, String name);
```

`CollectionTranscriptRepository extends JpaRepository<CollectionTranscriptEntity, CollectionTranscriptId>`:

```java
boolean existsByCollection_IdAndUserTranscript_Id(UUID collectionId, UUID userTranscriptId);
long countByCollection_Id(UUID collectionId);
void deleteByCollection_IdAndUserTranscript_Id(UUID collectionId, UUID userTranscriptId);
// batch counts for list view (avoid N+1):
@Query("SELECT ct.collection.id AS id, COUNT(ct) AS cnt FROM CollectionTranscriptEntity ct " +
       "WHERE ct.collection.userId = :userId GROUP BY ct.collection.id")
List<CollectionCountProjection> countsByUser(@Param("userId") UUID userId);
// member page: fetch the user_transcripts in a collection, newest-added first
@Query("SELECT ct.userTranscript FROM CollectionTranscriptEntity ct " +
       "WHERE ct.collection.id = :collectionId ORDER BY ct.addedAt DESC")
Page<UserTranscriptEntity> findMembers(@Param("collectionId") UUID collectionId, Pageable pageable);
```

> Member paging may instead reuse the existing custom-repository style
> (`CustomUserTranscriptRepository`/`UserTranscriptRepositoryImpl`) so the
> response is built identically to `pagedFilteredTranscripts`. Either approach
> must end at `TranscriptPageResponse` (§8).

### 7.5 `CollectionService` (`@Service`, `@Transactional` writes)
Constructor injection. CRUD mirrors `UserSubcategoryService`
(`normaliseName`, `validateUserId`, `ensureNameIsAvailable`, `getOwnedCollection`
via `findByIdAndUserId`). Add/remove mirror the `setSubcategory` double-ownership
pattern. Illustrative:

```java
@Transactional
public void addTranscript(UUID userId, UUID collectionId, UUID userTranscriptId) {
    CollectionEntity collection = getOwnedCollection(userId, collectionId)   // 404 if not owned
        .orElseThrow(() -> new CollectionNotFoundException(...));
    UserTranscriptEntity transcript = userTranscriptRepository
        .findByIdAndUserId(userTranscriptId, userId)                          // 404 if not owned
        .orElseThrow(() -> new TranscriptNotFoundException(...));
    if (joinRepo.existsByCollection_IdAndUserTranscript_Id(collectionId, userTranscriptId)) {
        return; // idempotent no-op — already a member, see §9
    }
    joinRepo.save(new CollectionTranscriptEntity(collection, transcript));
}
```

`removeTranscript`, `listMembers`, `findByUser` (with counts), and
`getOwnedCollection` follow the same ownership-first shape.

### 7.6 `CollectionMapper` (`@Component`)
`toDto(CollectionEntity, long transcriptCount)` → `CollectionDto`, mirroring
`UserSubcategoryMapper`.

### 7.7 DTOs (records, like existing)

```java
public record CollectionDto(UUID id, String name, String description,
                            long transcriptCount, Instant createdAt, Instant updatedAt,
                            Boolean contains) {} // contains: non-null ONLY on ?transcriptId= variant (see §4.5); null otherwise
public record CreateCollectionRequest(@NotBlank @Size(max = 255) String name,
                                      @Size(max = 4000) String description) {}
public record UpdateCollectionRequest(@Size(max = 255) String name,
                                      @Size(max = 4000) String description) {} // null = unchanged
```

(`CreateSubcategoryRequest` uses `@NotBlank`; update DTO leaves fields nullable
for partial update, exactly like `UpdateSubcategoryRequest`.)

### 7.8 `CollectionController` (`@RestController @RequestMapping("/api/v1")`)
`@AuthenticationPrincipal UserPrincipal principal` + `requireUser(principal)`,
mirroring `CategoryController`. (Note: `TranscriptController` uses
`Authentication authentication`; `CategoryController` uses `UserPrincipal` — we
follow `CategoryController` since the CRUD slice is the closer template.) Endpoints
per §4.4; create/list return `CollectionDto`, members return
`TranscriptPageResponse`, add/remove return `200`/`204`.

---

## 8. Member listing reuse

`GET /api/v1/collections/{id}/transcripts` returns the existing
**`TranscriptPageResponse`** of **`TranscriptDtoWithAliases`**, built via the
same `videoMapper.buildResponse(baseTranscript, userTranscript)` used by
`pagedFilteredTranscripts`. Paging params mirror `/page`: `page` (default 0,
clamped `>= 0`), `size` (default 50, clamped `1..100`). This lets iOS reuse
`TranscriptCard` and existing decoding with zero new render code.

---

## 9. Error handling & edge cases

| Case | Behaviour |
|---|---|
| Duplicate collection name for same user | **409 Conflict** — `ensureNameIsAvailable` via `findByUserIdAndNameIgnoreCase` (mirrors subcategory dedup). |
| Add a transcript already in the collection | **Idempotent no-op, return 200** with current membership (see decision below). |
| Add/remove a transcript not owned by user | **404** (`TranscriptNotFoundException`) — never leak existence of others' rows. |
| Add to a collection not owned by user | **404** (`CollectionNotFoundException`). |
| Remove a transcript that isn't a member | **204** (idempotent). |
| Delete a non-empty collection | **Allowed**; join rows cascade, transcripts kept. |
| Empty collection | Valid; member list returns empty `items` with `totalItems = 0`. |
| Blank / too-long name | **400** via Bean Validation (`@NotBlank`, `@Size(max=255)`); service also `normaliseName` trims. |
| Unauthenticated | `requireUser` throws → 401/400 via existing global handler. |
| Create when at **200** collections | **422** `CollectionLimitExceededException` (see §4.7). |
| Add when collection at **5000** members | **422** `CollectionLimitExceededException` (see §4.7). |
| Reorder body containing an id that is **not a member** | **400** — reject; message gives the count of unknown ids, not the ids themselves. (Partial sets of valid members are allowed — see §4.6.) |
| Reorder with empty / single-element array | **204** no-op (nothing to reorder). |
| Reorder a collection not owned by user | **404** (`CollectionNotFoundException`). |

**Add-duplicate decision: idempotent no-op (200), not 409.** Justification:
adding from a chip/toggle UI is naturally repeatable (double-tap, retry after a
flaky network); idempotency makes the client trivial and the DB composite PK
already guarantees uniqueness, so a duplicate insert is a harmless no-op rather
than a user-facing error. Removal is idempotent for symmetry.

---

## 10. iOS (Scoop) changes

Mirror the `CategoryService`/`Subcategory` slice; fetch **live** (no SwiftData
persistence) exactly like categories — collections change frequently and are
small, and the existing services already fetch on demand.

- `models/api/CollectionResponse.swift` — `Codable` mirroring `CollectionDto`
  (`id, name, description?, transcriptCount, createdAt, updatedAt`), like
  `SubcategoryResponse.swift`.
- `models/domain/Collection.swift` — `struct Collection: Identifiable`
  (`id: UUID, name, transcriptCount`), like `Subcategory.swift`.
- `models/mapper/` — `CollectionResponse → Collection` mapper.
- `service/CollectionService.swift` — static methods over `HTTPClient.request`
  with `NetworkError` mapping, mirroring `CategoryService`:
  `getCollections`, `createCollection`, `updateCollection`, `deleteCollection`,
  `addTranscript(collectionId:userTranscriptId:)`,
  `removeTranscript(...)`, `getCollectionTranscripts(collectionId:page:size:)`
  (decodes the existing `TranscriptPageResponse`/`TranscriptResponse`).
  `HTTPError.conflict` already exists → surface duplicate-name as a friendly
  message.
- **Collection picker sheet** — `views/Feed/CollectionPickerSheet.swift`,
  mirroring `SubcategoryPickerSheet` (chips + create-in-place), but multi-select
  toggles (a transcript can be in many). Reached from `TranscriptCard` and the
  transcript detail. Each toggle calls add/remove.
- **Collections browse** — a list view (`views/Collections/CollectionsScreen.swift`
  + view model) showing collections with counts, and a **collection detail**
  reusing `TranscriptCard` to render members from the paginated (position-ordered) endpoint.
  The detail supports **drag-to-reorder** (`.onMove`/`EditButton` on the member list),
  which calls `PATCH /collections/{id}/order` with the new id order (see §4.6).
- `service/CollectionService.swift` also gains `reorderTranscripts(collectionId:userTranscriptIds:)`.
- **Navigation** — add a "Collections" section/entry. Recommendation: a new tab
  or a section on `DashboardScreen` alongside the existing `CategoryGroup`
  browse, since collections are a parallel browse axis to categories.

---

## 11. Testing strategy

- **Service unit tests** (`CollectionServiceTest`, nested one layer deep by
  method-under-test per AGENTS.md): `createCollection` dedup/normalise/ownership;
  `addTranscript` ownership of both sides + idempotency; `removeTranscript`
  idempotency; `getOwnedCollection` cross-user → not found.
- **Repository / Testcontainers** (`CollectionTranscriptRepositoryTest`, reusing
  the existing PostgreSQL Testcontainers setup as other `*RepositoryTest`):
  composite-PK uniqueness, `countByCollection_Id`, and **cascade behaviour** —
  deleting a collection removes join rows; deleting a `user_transcript` removes
  its join rows; neither deletes the other side's owning rows.
- **Controller tests** (`CollectionControllerTest`) mirroring existing controller
  test style: status codes for create/list/add/remove/delete, 404 on
  cross-user, 409 on duplicate name, paginated member response shape.
- **iOS** service/decoding tests: `CollectionResponse` decodes sample JSON;
  `CollectionService` maps `HTTPError.conflict → NetworkError`, and member page
  decodes into `TranscriptResponse`.

---

## 12. Open questions / assumptions

1. ~~**Controller principal style**~~ **RESOLVED:** `@AuthenticationPrincipal UserPrincipal`
   (mirrors `CategoryController`, the slice template). Confirmed the codebase mixes
   `/api/v1` (Category/Transcript) and `/api/<noun>`; `/api/v1/collections` is correct here.
2. ~~**Manual ordering**~~ **RESOLVED (see §4.6):** YES in v1 — `position` column,
   append-on-add, `PATCH /collections/{id}/order` reorder, read ordered by `position`.
3. ~~**List flag for a transcript**~~ **RESOLVED (see §4.5):** use
   `GET /collections?transcriptId=...` returning a transient `contains: boolean` on each
   `CollectionDto`. **No** `/membership` endpoint and **no** collection data on the
   transcript DTO — membership is a join property, surfaced collection-side on demand only.
4. ~~**Member sort**~~ **RESOLVED (see §4.6):** order by `position ASC`, `added_at ASC` tiebreak.
5. ~~**Limits**~~ **RESOLVED (see §4.7):** soft caps — 200 collections/user, 5000
   transcripts/collection, enforced in service with **422**.
```
