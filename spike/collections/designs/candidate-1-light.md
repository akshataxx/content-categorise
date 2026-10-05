# Collections — candidate 1 design package (architect Phase B)

Shape: **relative-move ordering over a sparse `BIGINT` sort key, keyset paging by "after this transcript", and per-owner row locks for limits.** Written 2026-10-05 against backend `main @ 86f6dc5` and iOS `main @ 9e22533`. Read-only; no repo edited. Prior files in this directory are from an earlier run and were not read.

Evidence labels: **[M]** measured, meaning read in code now with file:line, or arithmetic on stated figures. **[I]** inferred from code or Postgres/Hibernate semantics, not executed. **[G]** guess, to be confirmed by the spikes in §12.

---

## 1. Problem

Collections are per-person, ordered lists of up to 5,000 of the person's saved transcripts (`user_transcripts`), up to 200 per person. Three things in the existing system make the shape non-obvious.

1. **Ordering with partial load.** No position/order column exists anywhere (grounding). The iOS detail screen loads 50 at a time (`CollectionDetailViewModel.swift:12`) and the dormant client reorders by sending *all loaded IDs* (`CollectionDetailViewModel.swift:44-52`). With 50 of 5,000 loaded, that request is either wrong or ambiguous. A full list of 5,000 IDs is ≈200 KB (N4, measured by PM).
2. **Limits and uniqueness under concurrency.** The codebase's only precedent is check-then-act with a case-sensitive DB unique and a case-insensitive service check (`UserSubcategoryService.java:81-87`, `V24__add_user_subcategory.sql`). That races. `DataIntegrityViolationException` falls through to the generic handler, which logs it and returns 500 with the SQL text (`GlobalExceptionHandler.java:119-133`) [M]. That would also put a collection name in the logs, against N6.
3. **Tests don't see Flyway.** The test profile builds the schema from entities (`application-test.properties:10,15`) [M]. Functional unique indexes, `ON DELETE` actions and CHECKs written only in Flyway are invisible to tests unless the entities declare them.

Constraints honoured:
- Deletion paths can't break. Bulk transcript delete calls `deleteAll` inside one transaction (`TranscriptService.java:233-270`) [M]. Account delete calls nine explicit `deleteByUserId` methods, then `deleteById` (`UserService.java:113-132`) [M].
- Scope by `userId` in the query. Not-yours is 404.
- One server, and a Hikari pool of 10 shared with the job poller.
- Pages default to 50, max 100, clamped as in `TranscriptController.java:64-87` [M].

## 2. Contract decision (register row 6)

**Keep the existing iOS contract where it is sound, and replace three parts of it.** No client is affected. `CollectionService` is never configured (`ScoopApp.swift:43-50`, which configures seven other services but not this one) [M], and the Home entry was removed in 71a4473. Every changed line is therefore an iOS edit, not a compatibility break.

| Existing iOS expectation (`CollectionService.swift`) | Decision | Why |
|---|---|---|
| `GET /api/v1/collections`, `?transcriptId=` with `contains` (L23-51) | **Keep** | ≤200 rows; unpaged is fine. `contains` already drives picker ticks (`CollectionPickerViewModel.swift:26`). |
| `POST /collections {name, description?}`, `PATCH /{id} {name?, description?}` (L53-87) | **Keep paths and verbs; drop `description`** (out of scope). `POST` gains an optional `initialTranscriptId`. | "Create from picker" (R1) becomes one atomic write instead of create-then-add (N3). |
| `DELETE /{id}`, `POST /{id}/transcripts {userTranscriptId}`, `DELETE /{id}/transcripts/{utId}` (L89-137) | **Keep** | They already have the right granularity: one tick is one atomic request. |
| `GET /{id}/transcripts?page=&size=` → `TranscriptPageResponse` (L139-153) | **Replace** with `?after=<userTranscriptId>&size=` → `CollectionTranscriptPage{items,totalItems,hasNext}` | With offset paging, deleting one loaded row makes `page+1` skip one item (worked example in §6.3). Keyset by "after this transcript" doesn't. |
| `PATCH /{id}/order {userTranscriptIds:[…]}` (L155-172) | **Replace** with `PUT /{id}/transcripts/{utId}/position {after: utId \| null}` | See §6. The move request is 48 B instead of ≈200 KB, writes 1 row instead of up to 5,000, and only needs IDs that are already loaded. |
| Errors collapse to `.requestFailed` (L15-21); 404/409 bodies dropped (`HTTPClient.swift:146-147,155-156`) | **Replace**: `ErrorResponse` gains `code`; `HTTPError.notFound/.conflict` carry `Data?` | R12 needs one message per error. The `.notFound`/`.conflict` cases have only one use outside `HTTPClient.swift` [M: grep], so carrying the body is cheap. |

## 3. Usage (caller's view, written first)

### 3.1 iOS service surface (derived spec)

```swift
// Scoop/service/CollectionService.swift — completion style kept (no unsolicited async refactor)
enum CollectionError: Error, Equatable {
    case nameBlank, nameTooLong, nameTaken          // 400, 400, 409
    case collectionLimitReached, collectionFull     // 422 (200 per person / 5,000 per collection)
    case collectionGone, transcriptGone, itemGone   // 404
    case pageCursorGone                             // 409 → reload from top
    case tryAgain                                   // 503 lock timeout / deadlock → "Couldn't save"
    case offline, failed                            // transport / anything else
    var message: String { /* one fixed string per case (R12) */ fatalError("not implemented") }
}

static func getCollections(transcriptId: UUID? = nil, completion: @escaping (Result<[CollectionResponse], CollectionError>) -> Void)
static func createCollection(name: String, initialTranscriptId: UUID? = nil, completion: @escaping (Result<CollectionResponse, CollectionError>) -> Void)
static func renameCollection(collectionId: UUID, name: String, completion: @escaping (Result<CollectionResponse, CollectionError>) -> Void)
static func deleteCollection(collectionId: UUID, completion: @escaping (Result<Void, CollectionError>) -> Void)
static func addTranscript(collectionId: UUID, userTranscriptId: UUID, completion: @escaping (Result<Void, CollectionError>) -> Void)
static func removeTranscript(collectionId: UUID, userTranscriptId: UUID, completion: @escaping (Result<Void, CollectionError>) -> Void)
static func getCollectionTranscripts(collectionId: UUID, after: UUID?, size: Int, completion: @escaping (Result<CollectionTranscriptPage, CollectionError>) -> Void)
static func moveTranscript(collectionId: UUID, userTranscriptId: UUID, after: UUID?, completion: @escaping (Result<Void, CollectionError>) -> Void)
```

### 3.2 Call site: reorder with only part of the list loaded (the hard path)

```swift
// CollectionDetailViewModel — replaces move(from:to:) at L44-52
private var confirmed: [Transcript] = []        // last server-acknowledged order of the loaded prefix ("saved order", R9)
private var moveQueue = SerialTaskQueue()       // moves go out one at a time, in drag order

func move(from source: IndexSet, to destination: Int) {
    guard source.count == 1, let from = source.first else { return }   // List drag moves one row
    let movedId = transcripts[from].id
    transcripts.move(fromOffsets: source, toOffset: destination)      // optimistic
    let newIndex = transcripts.firstIndex { $0.id == movedId }!
    let after: UUID? = newIndex == 0 ? nil : transcripts[newIndex - 1].id   // anchor is always a loaded row
    let snapshotAfterMove = transcripts
    moveQueue.enqueue { [self] in
        let r = await CollectionService.moveTranscriptAsync(collectionId, movedId, after: after)
        switch r {
        case .success: confirmed = snapshotAfterMove
        case .failure(let e):
            transcripts = confirmed; moveQueue.cancelPending(); errorMessage = e.message   // back to saved order
            if e == .itemGone || e == .collectionGone { await load() }                   // someone else changed it
        }
    }
}
```

### 3.3 Call site: paging that survives moves and removes

```swift
func loadMoreIfNeeded(current item: Transcript) async {
    guard hasNext, !isLoadingMore, item.id == transcripts.last?.id else { return }   // adds the missing in-flight guard
    isLoadingMore = true; defer { isLoadingMore = false }
    switch await fetch(after: transcripts.last?.id) {          // cursor = my last row, whatever moves/removes happened
    case .success(let p): transcripts.append(contentsOf: p.items.toDomain()); confirmed = transcripts; hasNext = p.hasNext
    case .failure(.pageCursorGone): await load()                // last row was removed on another device
    case .failure(let e): errorMessage = e.message
    }
}
```

### 3.4 Call sites: picker (R1, R6, R7, R8) and offline (R13)

```swift
// CollectionPickerViewModel.toggle — unchanged structure (L33-52), typed error now shown
if case .failure(let e) = result { selectedIds.insert(collection.id); errorMessage = e.message }
// "New collection" row in the picker: one request, applies fully or not at all
CollectionService.createCollection(name: typed, initialTranscriptId: userTranscriptId) { … }
// Picker entry points (FeedScreen dock L298-306/407-414, detail): disabled when
if case .authenticated(_, .offline) = sessionManager.state { /* OfflineUnavailableView(title: "Collections unavailable offline") */ }
```

The wire never carries a sort key, rank, page number or version. The client only names transcripts it can see.

## 4. Shape

### 4.1 Data structures (Flyway `V28__create_collections.sql`)

```sql
CREATE TABLE collections (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,             -- display form: stripped, NFC; ≤100 code points
    name_key    TEXT NOT NULL,                     -- derived in Java: name.toLowerCase(Locale.ROOT); never set independently
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_collections_user_name_key UNIQUE (user_id, name_key),   -- also the A–Z list index (R4)
    CONSTRAINT ck_collections_name_not_blank CHECK (char_length(btrim(name)) > 0)
);

CREATE TABLE collection_items (
    collection_id       UUID NOT NULL REFERENCES collections(id)      ON DELETE CASCADE,   -- R3, R11
    user_transcript_id  UUID NOT NULL REFERENCES user_transcripts(id) ON DELETE CASCADE,   -- R10
    sort_key            BIGINT NOT NULL,   -- sparse; only ordering matters; never on the wire
    added_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_collection_items PRIMARY KEY (collection_id, user_transcript_id)   -- re-add no-op, contains lookup
);
CREATE INDEX idx_collection_items_order ON collection_items (collection_id, sort_key, user_transcript_id);  -- paging, neighbours, max/min
CREATE INDEX idx_collection_items_ut    ON collection_items (user_transcript_id);                         -- FK cascade from user_transcripts
```

Load-bearing decisions:

- **`name_key` column instead of a `lower(name)` functional index.** A plain `UNIQUE (user_id, name_key)` can be declared on the entity (`@UniqueConstraint`), so `ddl-auto=create` builds it and tests see it. It also serves as the `ON CONFLICT` target, which lets a duplicate create return 0 rows instead of raising, so the name never reaches an error message or the Postgres log. Lowercasing happens only in `CollectionName.parse`, which keeps it in one place (per single-source-of-truth). We don't add `CHECK (name_key = lower(name))` because Java `Locale.ROOT` and Postgres `lower()` disagree on some non-ASCII characters [I].
- **Ranks aren't unique in the schema.** They're unique by construction: every sort-key write happens under the collection row lock, and append, move and renumber each produce distinct keys. `ORDER BY sort_key, user_transcript_id` is total, so even a tie would still sort deterministically. A non-deferrable `UNIQUE(collection_id, sort_key)` would break the single-statement renumber mid-statement, and a deferrable one can't be declared on the entity [I].
- **Counts are derived** (`count(*)` on the PK prefix), not stored. The R10/R11 cascades change membership without touching Java, so a stored counter would need a trigger, which tests can't see, or a decrement in every delete path. Spike S1(a) checks the cost at 200×5,000.
- **No `user_id` on `collection_items`.** Ownership comes from the parent collection, and adds check transcript ownership in the insert itself (§4.3). Declaring "membership stays within one person" in the schema would need composite FKs plus a new `UNIQUE(id,user_id)` on `user_transcripts`. See Tradeoffs.

Entity mirror (so the test schema has the same invariants). Hibernate 6.6 DDL emitting `@OnDelete` and `@Check` is **[I]**, confirmed by spike S2.

```java
@Entity @Table(name = "collections",
  uniqueConstraints = @UniqueConstraint(name = "uq_collections_user_name_key", columnNames = {"user_id", "name_key"}))
@Check(name = "ck_collections_name_not_blank", constraints = "char_length(btrim(name)) > 0")
public class CollectionEntity {
    @Id UUID id;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "user_id") @OnDelete(action = OnDeleteAction.CASCADE) UserEntity user;
    @Column(nullable = false, length = 100) String name;
    @Column(name = "name_key", nullable = false, columnDefinition = "text") String nameKey;
    @Column(nullable = false) Instant createdAt; @Column(nullable = false) Instant updatedAt;
}

@Entity @Table(name = "collection_items",
  indexes = { @Index(name = "idx_collection_items_order", columnList = "collection_id, sort_key, user_transcript_id"),
              @Index(name = "idx_collection_items_ut", columnList = "user_transcript_id") })
public class CollectionItemEntity {
    @EmbeddedId CollectionItemId id;                       // (collectionId, userTranscriptId)
    @MapsId("collectionId") @ManyToOne(fetch = LAZY) @JoinColumn(name = "collection_id") @OnDelete(action = OnDeleteAction.CASCADE) CollectionEntity collection;
    @MapsId("userTranscriptId") @ManyToOne(fetch = LAZY) @JoinColumn(name = "user_transcript_id") @OnDelete(action = OnDeleteAction.CASCADE) UserTranscriptEntity userTranscript;
    @Column(name = "sort_key", nullable = false) long sortKey;
    @Column(nullable = false) Instant addedAt;
}
```

The entities are only for schema and test parity. Every write goes through native statements that run immediately (§4.3), so no write waits for the flush at commit, where the grounding says catch-and-requery fails.

### 4.2 Domain types (invariants in types)

```java
// domain/model/CollectionName.java — the only place a name is validated or case-folded
public record CollectionName(String display, String key) {
    public static final int MAX_CODE_POINTS = 100;         // matches VARCHAR(100), which counts characters, not UTF-16 units
    /** strip() → NFC → blank? NAME_BLANK : codePointCount>100 ? NAME_TOO_LONG : new(display, display.toLowerCase(Locale.ROOT)) */
    public static CollectionName parse(String raw) { throw new UnsupportedOperationException("not implemented"); }
    @Override public String toString() { return "CollectionName[redacted]"; }   // N6: a stray log line can't leak it
}

// domain/model/Placement.java — "after null = top" as a type, not a nullable
public sealed interface Placement {
    record Top() implements Placement {}
    record After(UUID userTranscriptId) implements Placement {}
    static Placement fromWire(UUID afterOrNull) { throw new UnsupportedOperationException("not implemented"); }
}

// domain/model/SortKeys.java — pure, unit-tested; nothing outside the service knows sort keys exist
final class SortKeys {
    static final long STEP = 1L << 32;                     // ≥31 bisections per gap before a renumber [M arithmetic]
    static final long RENUMBER_BASE = STEP;                // renumber writes STEP, 2·STEP, … 5000·STEP ≈ 2.1e13 ≪ 9.2e18
    static long append(OptionalLong max)            { throw new UnsupportedOperationException("not implemented"); } // max+STEP, or STEP if empty; empty-on-overflow → renumber
    static long top(long currentFirst)              { throw new UnsupportedOperationException("not implemented"); } // first−STEP; underflow → renumber
    /** strictly between prev and next; empty when next−prev < 2 → caller renumbers then retries once */
    static OptionalLong between(long prev, OptionalLong next) { throw new UnsupportedOperationException("not implemented"); }
}

// domain/model/CollectionSummary.java
public record CollectionSummary(UUID id, String name, int transcriptCount, Instant createdAt, Instant updatedAt, Boolean contains) {}

// exception/CollectionError.java — code ↔ status ↔ user-safe message, one table (encode-lessons-in-structure)
public enum CollectionError {
    NAME_BLANK           (BAD_REQUEST,          "COLLECTION_NAME_BLANK",     "Enter a name for the collection."),
    NAME_TOO_LONG        (BAD_REQUEST,          "COLLECTION_NAME_TOO_LONG",  "Collection names can be up to 100 characters."),
    NAME_TAKEN           (CONFLICT,             "COLLECTION_NAME_TAKEN",     "You already have a collection with that name."),
    COLLECTION_LIMIT     (UNPROCESSABLE_ENTITY, "COLLECTION_LIMIT_REACHED",  "You can have up to 200 collections."),
    COLLECTION_FULL      (UNPROCESSABLE_ENTITY, "COLLECTION_FULL",           "A collection can hold up to 5,000 transcripts."),
    COLLECTION_NOT_FOUND (NOT_FOUND,            "COLLECTION_NOT_FOUND",      "This collection no longer exists."),
    TRANSCRIPT_NOT_FOUND (NOT_FOUND,            "TRANSCRIPT_NOT_FOUND",      "This transcript no longer exists."),
    ITEM_NOT_FOUND       (NOT_FOUND,            "COLLECTION_ITEM_NOT_FOUND", "That transcript is no longer in this collection."),
    INVALID_MOVE         (BAD_REQUEST,          "COLLECTION_INVALID_MOVE",   "A transcript can't be placed after itself."),
    PAGE_CURSOR_GONE     (CONFLICT,             "COLLECTION_PAGE_CURSOR_GONE","This collection changed. Reloading."),
    TRY_AGAIN            (SERVICE_UNAVAILABLE,  "COLLECTION_TRY_AGAIN",      "Couldn't save. Try again.");
    // fields + ctor elided
}
public final class CollectionException extends RuntimeException {
    private final CollectionError error;                   // message = error.userMessage(); never contains input
    public CollectionException(CollectionError error) { super(error.userMessage(), null, false, false); this.error = error; }
}
```

### 4.3 Repository (native, immediate, scoped by owner)

```java
public interface CollectionRepository extends JpaRepository<CollectionEntity, UUID> {
    /** Per-person serialisation point for create/rename (200-limit, name uniqueness). FOR NO KEY UPDATE does not block
     *  other transactions' FK KEY SHARE locks on users (e.g. inserting user_transcripts) [I]. */
    @Query(value = "SELECT id FROM users WHERE id = :userId FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockOwner(UUID userId);

    /** Per-collection serialisation point for add/move/renumber; doubles as the ownership check (0 rows ⇒ 404). */
    @Query(value = "SELECT id FROM collections WHERE id = :id AND user_id = :userId FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockOwned(UUID id, UUID userId);

    @Query(value = "SELECT count(*) FROM collections WHERE user_id = :userId", nativeQuery = true)
    long countOwned(UUID userId);

    /** 1 = created, 0 = name_key taken. No exception, so nothing reaches logs. */
    @Modifying @Query(value = """
        INSERT INTO collections (id, user_id, name, name_key, created_at, updated_at)
        VALUES (:id, :userId, :name, :nameKey, now(), now())
        ON CONFLICT (user_id, name_key) DO NOTHING""", nativeQuery = true)
    int insertIfNameFree(UUID id, UUID userId, String name, String nameKey);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM collections WHERE user_id = :userId AND name_key = :nameKey AND id <> :exceptId)", nativeQuery = true)
    boolean nameTakenByOther(UUID userId, String nameKey, UUID exceptId);

    @Modifying @Query(value = "UPDATE collections SET name = :name, name_key = :nameKey, updated_at = now() WHERE id = :id AND user_id = :userId", nativeQuery = true)
    int renameOwned(UUID id, UUID userId, String name, String nameKey);

    @Modifying @Query(value = "DELETE FROM collections WHERE id = :id AND user_id = :userId", nativeQuery = true)
    int deleteOwned(UUID id, UUID userId);

    /** Account delete: one statement; items cascade in the DB. */
    @Modifying @Query(value = "DELETE FROM collections WHERE user_id = :userId", nativeQuery = true)
    int deleteAllOwnedBy(UUID userId);

    /** A–Z ignoring case, counts derived; contains only when transcriptId != null. */
    @Query(value = """
        SELECT c.id, c.name, c.created_at AS createdAt, c.updated_at AS updatedAt,
               (SELECT count(*) FROM collection_items ci WHERE ci.collection_id = c.id) AS transcriptCount,
               CASE WHEN CAST(:transcriptId AS uuid) IS NULL THEN NULL
                    ELSE EXISTS (SELECT 1 FROM collection_items ci WHERE ci.collection_id = c.id
                                 AND ci.user_transcript_id = CAST(:transcriptId AS uuid)) END AS contains
        FROM collections c WHERE c.user_id = :userId ORDER BY c.name_key, c.id""", nativeQuery = true)
    List<CollectionRow> listOwned(UUID userId, UUID transcriptId);
}

public interface CollectionItemRepository extends JpaRepository<CollectionItemEntity, CollectionItemId> {
    long countByIdCollectionId(UUID collectionId);
    boolean existsByIdCollectionIdAndIdUserTranscriptId(UUID collectionId, UUID userTranscriptId);

    /** Inserts only if the transcript is the caller's; 0 ⇒ caller re-checks "already in" vs "not yours/gone". */
    @Modifying @Query(value = """
        INSERT INTO collection_items (collection_id, user_transcript_id, sort_key, added_at)
        SELECT :collectionId, ut.id, :sortKey, now() FROM user_transcripts ut
        WHERE ut.id = :userTranscriptId AND ut.user_id = :userId
        ON CONFLICT (collection_id, user_transcript_id) DO NOTHING""", nativeQuery = true)
    int insertOwned(UUID collectionId, UUID userTranscriptId, UUID userId, long sortKey);

    @Query(value = "SELECT max(sort_key) FROM collection_items WHERE collection_id = :c", nativeQuery = true) Long maxSortKey(UUID c);
    @Query(value = "SELECT user_transcript_id AS id, sort_key AS sortKey FROM collection_items WHERE collection_id = :c ORDER BY sort_key, user_transcript_id LIMIT 1", nativeQuery = true) Optional<KeyedItem> firstItem(UUID c);
    @Query(value = "SELECT sort_key FROM collection_items WHERE collection_id = :c AND user_transcript_id = :ut", nativeQuery = true) Optional<Long> sortKeyOf(UUID c, UUID ut);
    /** Immediate successor of the anchor (may be the moved row itself ⇒ no-op); uses idx_collection_items_order. */
    @Query(value = """
        SELECT user_transcript_id AS id, sort_key AS sortKey FROM collection_items
        WHERE collection_id = :c AND (sort_key, user_transcript_id) > (:prevKey, :prevId)
        ORDER BY sort_key, user_transcript_id LIMIT 1""", nativeQuery = true)
    Optional<KeyedItem> successorOf(UUID c, long prevKey, UUID prevId);

    @Modifying @Query(value = "UPDATE collection_items SET sort_key = :k WHERE collection_id = :c AND user_transcript_id = :ut", nativeQuery = true)
    int setSortKey(UUID c, UUID ut, long k);

    /** One statement, ≤5,000 rows; order preserved; keys STEP apart again. */
    @Modifying @Query(value = """
        UPDATE collection_items ci SET sort_key = r.rn * :step
        FROM (SELECT user_transcript_id, row_number() OVER (ORDER BY sort_key, user_transcript_id) AS rn
              FROM collection_items WHERE collection_id = :c) r
        WHERE ci.collection_id = :c AND ci.user_transcript_id = r.user_transcript_id""", nativeQuery = true)
    int renumber(UUID c, long step);

    @Modifying @Query(value = """
        DELETE FROM collection_items ci USING collections c
        WHERE ci.collection_id = c.id AND c.id = :c AND c.user_id = :userId AND ci.user_transcript_id = :ut""", nativeQuery = true)
    int removeOwned(UUID c, UUID ut, UUID userId);

    @Query(value = """
        SELECT user_transcript_id FROM collection_items
        WHERE collection_id = :c AND (CAST(:afterKey AS bigint) IS NULL OR (sort_key, user_transcript_id) > (:afterKey, :afterId))
        ORDER BY sort_key, user_transcript_id LIMIT :limit""", nativeQuery = true)
    List<UUID> pageIds(UUID c, Long afterKey, UUID afterId, int limit);
}
```

### 4.4 Service: one deep module

```java
@Service
public class CollectionService {
    static final int MAX_COLLECTIONS = 200, MAX_ITEMS = 5_000;

    public List<CollectionSummary> list(UUID userId, @Nullable UUID transcriptId) {
        // if transcriptId != null && !userTranscripts.existsByIdAndUserId(...) → TRANSCRIPT_NOT_FOUND
        throw new UnsupportedOperationException("not implemented");
    }

    @Transactional  // all-or-nothing incl. initial add (R1 from picker)
    public CollectionSummary create(UUID userId, String rawName, @Nullable UUID initialTranscriptId) {
        // name = CollectionName.parse(rawName)                       (400s before any lock)
        // SET LOCAL lock_timeout = '3s'
        // lockOwner(userId)                                          (serialises this person's create/rename)
        // countOwned(userId) >= 200 → COLLECTION_LIMIT               (fresh READ COMMITTED snapshot after the lock ⇒ sees peers' commits [I])
        // insertIfNameFree(...) == 0 → NAME_TAKEN
        // if initialTranscriptId: insertOwned(id, ut, userId, SortKeys.append(empty)) == 0 → TRANSCRIPT_NOT_FOUND (rolls back the create)
        throw new UnsupportedOperationException("not implemented");
    }

    @Transactional
    public CollectionSummary rename(UUID userId, UUID collectionId, String rawName) {
        // parse → lockOwner → nameTakenByOther → NAME_TAKEN → renameOwned == 0 → COLLECTION_NOT_FOUND
        // backstop: translate 23505 on uq_collections_user_name_key → NAME_TAKEN (never logged with detail)
        throw new UnsupportedOperationException("not implemented");
    }

    @Transactional
    public void delete(UUID userId, UUID collectionId) { /* deleteOwned == 0 → COLLECTION_NOT_FOUND; items cascade */ throw new UnsupportedOperationException("not implemented"); }

    @Transactional(readOnly = true)
    public CollectionTranscriptPage page(UUID userId, UUID collectionId, @Nullable UUID after, int size) {
        // ownership check (no lock) → 404
        // after != null: sortKeyOf(c, after) empty → PAGE_CURSOR_GONE
        // ids = pageIds(c, key, after, size + 1); hasNext = ids.size() > size; trim
        // rows = userTranscripts.findAllByIdInAndUserId(ids, userId)  (UserTranscriptRepository.java:85) → re-sort to ids order
        // items = rows.map(videoMapper::buildResponse) with per-request alias memo by categoryId (VideoMapper.java:113-118 is one query per item)
        // totalItems = countByIdCollectionId(c)
        throw new UnsupportedOperationException("not implemented");
    }

    @Transactional
    public void add(UUID userId, UUID collectionId, UUID userTranscriptId) {
        // SET LOCAL lock_timeout; lockOwned → COLLECTION_NOT_FOUND
        // exists(c, ut) → return (re-add no-op, even at 5,000)
        // countByIdCollectionId(c) >= 5000 → COLLECTION_FULL
        // insertOwned(c, ut, userId, SortKeys.append(maxSortKey(c))) == 0 → TRANSCRIPT_NOT_FOUND
        // 23503 (transcript deleted mid-flight, FK check waited on its row lock) → TRANSCRIPT_NOT_FOUND
        throw new UnsupportedOperationException("not implemented");
    }

    @Transactional
    public void remove(UUID userId, UUID collectionId, UUID userTranscriptId) {
        // removeOwned == 0: collection mine? → 204 (idempotent) : COLLECTION_NOT_FOUND. No lock: removal can't break limits or key uniqueness.
        throw new UnsupportedOperationException("not implemented");
    }

    @Transactional
    public void move(UUID userId, UUID collectionId, UUID userTranscriptId, Placement placement) {
        // SET LOCAL lock_timeout; lockOwned → COLLECTION_NOT_FOUND
        // After(a) with a == ut → INVALID_MOVE
        // movedKey = sortKeyOf(c, ut) → ITEM_NOT_FOUND
        // Top:      first = firstItem(c); first.id == ut → no-op (0 rows); else key = SortKeys.top(first.sortKey)
        // After(a): prevKey = sortKeyOf(c, a) → ITEM_NOT_FOUND
        //           succ = successorOf(c, prevKey, a); succ.id == ut → no-op (idempotent retry, 0 rows written)
        //           otherwise succ is already the correct lower neighbour (ut sits elsewhere), so no exclusion is needed
        //           key = SortKeys.between(prevKey, succ?.sortKey); empty → renumber(c, STEP), re-read prevKey/succ, recompute once
        // setSortKey(c, ut, key)
        throw new UnsupportedOperationException("not implemented");
    }
}
```

`SET LOCAL lock_timeout = '3s'` limits how long a stuck holder can keep one of the 10 shared pool connections waiting (N1). `55P03` (lock timeout) and `40P01` (deadlock) map to `TRY_AGAIN` 503, which R12 shows as "failed save". No lock-order cycle exists: create and rename take users → collection, add and move take only the collection, and remove/delete take no explicit lock [I]. Exceptions are translated inside the service's own transaction, which is safe here because the native statements have already run [I]. That is the opposite of the flush-at-commit trap at `VideoService.java:590-610`.

### 4.5 Controller and error mapping

```java
@RestController @RequestMapping("/api/v1/collections")
public class CollectionController {
    @GetMapping                                   ResponseEntity<List<CollectionResponse>> list(@RequestParam(required = false) UUID transcriptId, Authentication a);
    @PostMapping                                  ResponseEntity<CollectionResponse> create(@RequestBody CreateCollectionRequest body, Authentication a);   // 201
    @PatchMapping("/{id}")                        ResponseEntity<CollectionResponse> rename(@PathVariable UUID id, @RequestBody RenameCollectionRequest body, Authentication a);
    @DeleteMapping("/{id}")                       ResponseEntity<Void> delete(@PathVariable UUID id, Authentication a);                                 // 204
    @GetMapping("/{id}/transcripts")              ResponseEntity<CollectionTranscriptPage> page(@PathVariable UUID id, @RequestParam(required = false) UUID after,
                                                                                               @RequestParam(defaultValue = "50") int size, Authentication a); // clamp 1..100
    @PostMapping("/{id}/transcripts")             ResponseEntity<Void> add(@PathVariable UUID id, @RequestBody AddCollectionTranscriptRequest body, Authentication a); // 204
    @DeleteMapping("/{id}/transcripts/{utId}")    ResponseEntity<Void> remove(@PathVariable UUID id, @PathVariable UUID utId, Authentication a);       // 204
    @PutMapping("/{id}/transcripts/{utId}/position") ResponseEntity<Void> move(@PathVariable UUID id, @PathVariable UUID utId, @RequestBody MoveRequest body, Authentication a); // 204
    // every handler: UUID userId = ((UserPrincipal) a.getPrincipal()).getId(); not implemented
}
public record CreateCollectionRequest(String name, UUID initialTranscriptId) {}
public record RenameCollectionRequest(String name) {}
public record AddCollectionTranscriptRequest(UUID userTranscriptId) {}
public record MoveRequest(UUID after) {}                        // null ⇒ top
public record CollectionResponse(UUID id, String name, int transcriptCount, Instant createdAt, Instant updatedAt, Boolean contains) {}
public record CollectionTranscriptPage(List<TranscriptDtoWithAliases> items, long totalItems, boolean hasNext) {}

// GlobalExceptionHandler additions
@ExceptionHandler(CollectionException.class)
ResponseEntity<ErrorResponse> handleCollection(CollectionException ex, WebRequest r) {
    // status = ex.error().status(); body = new ErrorResponse(status, reason, ex.error().userMessage(), path, ex.error().code())
    // log at DEBUG with code only — never the message source, request body, or SQL detail (N6)
    throw new UnsupportedOperationException("not implemented");
}
// ErrorResponse: add nullable `code` (additive; existing clients ignore it). Malformed UUID / JSON on /api/v1/collections/** → 400 INVALID_REQUEST.
```

## 5. Endpoint table

| # | Method + path | Body | Success | Errors (status · code) | Req |
|---|---|---|---|---|---|
| 1 | `GET /api/v1/collections[?transcriptId=]` | — | 200 `[CollectionResponse]` A–Z, counts, `contains` iff `transcriptId` | 404 `TRANSCRIPT_NOT_FOUND` | R4, R8 |
| 2 | `POST /api/v1/collections` | `{name, initialTranscriptId?}` | 201 `CollectionResponse` | 400 `COLLECTION_NAME_BLANK` / `COLLECTION_NAME_TOO_LONG` · 409 `COLLECTION_NAME_TAKEN` · 422 `COLLECTION_LIMIT_REACHED` · 404 `TRANSCRIPT_NOT_FOUND` · 503 `COLLECTION_TRY_AGAIN` | R1, R6 |
| 3 | `PATCH /api/v1/collections/{id}` | `{name}` | 200 `CollectionResponse` | 400 name codes · 409 `COLLECTION_NAME_TAKEN` · 404 `COLLECTION_NOT_FOUND` · 503 | R2 |
| 4 | `DELETE /api/v1/collections/{id}` | — | 204 | 404 `COLLECTION_NOT_FOUND` (client treats as already gone) | R3 |
| 5 | `GET /api/v1/collections/{id}/transcripts?after=&size=` | — | 200 `{items,totalItems,hasNext}`; size clamped 1..100, default 50 | 404 `COLLECTION_NOT_FOUND` · 409 `COLLECTION_PAGE_CURSOR_GONE` | R5 |
| 6 | `POST /api/v1/collections/{id}/transcripts` | `{userTranscriptId}` | 204 (added or already in) | 404 `COLLECTION_NOT_FOUND` / `TRANSCRIPT_NOT_FOUND` · 422 `COLLECTION_FULL` · 503 | R6 |
| 7 | `DELETE /api/v1/collections/{id}/transcripts/{utId}` | — | 204 (removed or not in) | 404 `COLLECTION_NOT_FOUND` | R7 |
| 8 | `PUT /api/v1/collections/{id}/transcripts/{utId}/position` | `{after: utId \| null}` | 204 (also when already in place) | 400 `COLLECTION_INVALID_MOVE` · 404 `COLLECTION_NOT_FOUND` / `COLLECTION_ITEM_NOT_FOUND` · 503 | R9 |

Every other user's ID, or a missing one, gets 404 (scoped in SQL). No path produces 500 for a domain condition. Unique (23505), FK (23503), lock-timeout (55P03) and deadlock (40P01) are all translated.

## 6. The hard path: reorder 5,000 with 50 loaded

### 6.1 Request

`PUT /api/v1/collections/{cid}/transcripts/{movedId}/position` with body `{"after":"<uuid of the loaded row now above it>"}`, or `{"after":null}` for the top. A `List.onMove` drag moves one row, and its new upper neighbour is always a row the client has loaded. So the client never needs an ID it hasn't loaded, at any collection size.

### 6.2 Cost

| | This design | Existing full-list PATCH (5,000) |
|---|---|---|
| Request body | 48 B (`{"after":"` 10 + UUID 36 + `"}` 2); 14 B for top [M arithmetic] | ≈200 KB [M, N4] |
| Rows written, common case | **1** (`UPDATE … sort_key`; non-HOT because `sort_key` is indexed, so 1 heap tuple + 2 index entries) [I] | up to 5,000 |
| Rows written, gap exhausted | ≤5,000 once (renumber), then 1 | — |
| Worst amortised (adversary always drops into the same gap) | STEP = 2³² allows ≥31 bisections, so ≤(5,000+31)/32 ≈ **158 rows/move** [M arithmetic] | 5,000 |
| Reads | lock (PK), moved key (PK), anchor key (PK), successor (index, LIMIT 1): 4 index probes [I] | |
| Target set by this stage (N2) | **p90 < 200 ms server time at 5,000 items; renumber move p99 < 500 ms** | |

### 6.3 Why paging stays consistent

Invariant: **after any move by this client, its loaded rows are still a prefix of the collection in sort-key order.** The anchor is a loaded row, and the new key falls strictly between the anchor and its successor, which is either loaded or the first unloaded row. So no loaded row moves past an unloaded one [I, by construction of `between`]. The next page is therefore `after = <last loaded row>`, and that's correct whatever moves have happened.

Offset paging fails on an ordinary remove. Rows 0–49 are loaded and the user deletes row 10. The old row 50 is now at offset 49, and `page=1` (offset 50) never returns it. Keyset by "after my last row" returns it [I]. Adds append at `max+STEP`, after everything loaded. Changes from another device can still make the cursor row disappear: the server returns 409 `PAGE_CURSOR_GONE`, and the client reloads from the top. A row another device moves across the cursor can appear twice or not at all. The client de-duplicates by ID, and pull-to-refresh fixes the order. We accept that multi-device edge case.

Failed reorder (R9): the client keeps `confirmed`, the last acknowledged order. Moves are sent one at a time, and on any failure the client restores `confirmed`, drops queued moves and shows the code's message. On 404 it also reloads. Retrying the same move is harmless: if the item already sits after the anchor, the server writes 0 rows and returns 204.

## 7. Concurrency: limits and uniqueness without 500

| Race (two requests at once) | Mechanism | Outcome |
|---|---|---|
| Same name, both creates | Both `lockOwner` (users row, `FOR NO KEY UPDATE`). The second waits; after the lock, `INSERT … ON CONFLICT (user_id,name_key) DO NOTHING` returns 0 | 201 + 409 `COLLECTION_NAME_TAKEN`; also holds without the lock thanks to the unique constraint |
| 199 collections, two creates | Second waits on the lock. Its next statement takes a new READ COMMITTED snapshot, so `countOwned` = 200 [I] | 201 + 422 `COLLECTION_LIMIT_REACHED`; total stays 200 |
| Rename A→"x" while create "X" | Both take the users-row lock, so they serialise; `nameTakenByOther` or `ON CONFLICT` catches the second; unique constraint as backstop | 200/201 + 409 |
| 4,999 items, two adds of different transcripts | `lockOwned` on the collection row serialises; count → insert | 204 + 422 `COLLECTION_FULL`; stays 5,000 |
| Same transcript added twice | Serialised; second sees `exists` (or PK `ON CONFLICT`) | 204 + 204, one row |
| Add while the collection is deleted | `DELETE` waits for the add's row lock, or the add's `lockOwned` finds 0 rows | add 204 then cascade removes it, or 404 `COLLECTION_NOT_FOUND` |
| Add while the transcript is bulk-deleted | The FK check waits on the transcript row lock, then fails with 23503 → translated | 404 `TRANSCRIPT_NOT_FOUND`, transaction rolled back |
| Two moves in one collection (two devices) | Serialised by `lockOwned`; each is relative, so both apply in commit order | 204 + 204; keys stay distinct |

Why row locks and not the alternatives:
- `SERIALIZABLE` needs retry loops around every write and has no precedent here.
- Advisory locks have no precedent and aren't visible as rows.
- Counters need to stay in sync across the cascades.

The users row is the natural per-person serialisation point. `FOR NO KEY UPDATE` doesn't conflict with the `KEY SHARE` lock that FK inserts take, so unrelated writes like saving a transcript aren't blocked [I].

## 8. Deletion (R3, R10, R11)

- **R3 collection delete:** one `DELETE … WHERE id AND user_id`; items cascade in the DB. Transcripts stay because the FK runs items → user_transcripts, never the reverse. The client asks for confirmation when `transcriptCount > 0`.
- **R10 transcript delete, single or bulk ≤100:** no code change in `TranscriptService.deleteTranscripts` (`TranscriptService.java:233-270`). Hibernate deletes each `user_transcripts` row at flush. `ON DELETE CASCADE` on `collection_items.user_transcript_id` removes memberships in the same transaction, and counts drop because they're derived [I]. The cascade can't fail with an FK error, so the flush at commit, which sits outside that method's try (grounding), has nothing new to throw. Worst case for 100 transcripts each in 200 collections is 20,000 cascade deletes, measured in S1(e).
- **R11 account delete:** add one line at the top of `UserService.deleteAccount` (`UserService.java:119`): `collectionRepository.deleteAllOwnedBy(userId)`. It's one bulk native statement that cascades items. It runs before `userTranscriptRepository.deleteByUserId`, so the row-by-row derived delete (grounding) no longer has to cascade through items. `users ON DELETE CASCADE` is a second guard. The explicit call keeps the existing pattern and the per-repo verification in `UserServiceTest`, and it makes account delete work in the test schema even if `@OnDelete` were dropped. Worst case is 1,000,000 item rows in one transaction (S1(e)).

## 9. How tests prove N3/N4 when tests build the schema from entities

1. **Schema parity, in two layers.**
   - (a) Entities declare every invariant the tests rely on: `@UniqueConstraint(user_id,name_key)`, `@OnDelete(CASCADE)` on all three FKs, `@Check` not-blank and `length=100`. So the `ddl-auto=create` schema enforces them too (S2 confirms Hibernate emits them).
   - (b) A new `CollectionsMigrationParityIT` turns Flyway on for one class: `spring.flyway.enabled=true`, `ddl-auto=validate`, on image `pgvector/pgvector:pg15`, because V25 needs the extension.
     - It asserts `pg_constraint` has `uq_collections_user_name_key`, `confdeltype='c'` on the three FKs and `ck_collections_name_not_blank`.
     - It asserts the indexes exist.
     - Then it runs the R10/R11 cascade scenario against the real migrated schema.
   - Drift between Flyway and the entities fails this test.
2. **Concurrency tests on real Postgres** (`CollectionServiceConcurrencyIT`, Testcontainers, full context, no `@Transactional` on the test so each call commits).
   - Seeding: 199 collections, or 4,999 items via one `INSERT … SELECT generate_series` through `JdbcTemplate` (needs matching `base_transcripts`/`user_transcripts` rows from the same series).
   - Race: 8 threads released by a `CyclicBarrier`, 20 rounds.
   - Asserts:
     - exactly one success per slot;
     - every other call fails with `CollectionException` 409/422;
     - the final count is exactly 200 or 5,000;
     - no other exception type.
   - A test-only `CountDownLatch` hook isn't needed: the result is the same under any interleaving, and the barrier makes overlap likely. Also, temporarily removing the `lockOwner` call should make the test fail. Run that negative control once during implementation; it isn't kept in the suite.
3. **No 500, ever** (`CollectionControllerIT`, MockMvc against the real service and container). One test per row of §5 asserts status and `code`. The unique backstop is tested by calling `renameOwned` straight against a taken key and asserting the service translation gives `NAME_TAKEN`.
4. **Hard path** (`CollectionOrderingIT`). Seed 5,000 items, load 50 via the API, apply 200 random moves among the loaded rows, mirror each locally, then page with `after=`. Assert:
   - the concatenated pages equal the local model;
   - no duplicates;
   - total = 5,000.
   It also forces renumbering with 40 drops into one gap and checks order is preserved. `SortKeysTest` (pure) covers `between`, `top`, `append` and the overflow edges.
5. **R10/R11:** `TranscriptService.deleteTranscripts` with transcripts in 3 collections → memberships gone, counts dropped. `UserService.deleteAccount` with 2 collections → succeeds, and every Collections table is empty for the user. Update `UserServiceTest` to verify `deleteAllOwnedBy`.
6. **N6 logs:** an `OutputCaptureExtension` test runs every error path with the name `"zz-secret-name-7"` and asserts the string never appears in captured output.

## 10. NFR screen

| NFR | Meets | How | Risk |
|---|---|---|---|
| N1 available on one server | Yes | No new infrastructure. Lock waits are capped with `lock_timeout 3s` → 503 `TRY_AGAIN`, so the shared pool of 10 can't be pinned. | Account delete with 1M items holds a connection for its whole cascade [G]; S1(e) |
| N2 p90 < 500 ms | Yes [G until S1] | List: 200 correlated PK-prefix counts. Page: keyset index range + `findAllByIdInAndUserId` + mapper (alias lookups cached per category). Add/remove: PK probes + index max. Move target: p90 < 200 ms. | Derived counts at 200×5,000 with a stale visibility map (heap fetches) [G]; page payload with full transcript text is unmeasured |
| N3 atomic incl. two concurrent; 409/422/404 with codes; no 500 | Yes | One transaction per request; per-owner and per-collection row locks; `ON CONFLICT`; 23505/23503/55P03/40P01 translated; create+initial add atomic. | Hibernate wrapping of native-query constraint errors must expose the SQLState/constraint name for translation [I]; covered by §9.3 |
| N4 200 / 5,000 / 100 chars / 50–100 / reorder bound | Yes | Limits checked under the lock; `VARCHAR(100)` + code-point validation; size clamp; reorder is 48 B, not 200 KB. | iOS counts graphemes, the server counts code points. iOS must validate with `unicodeScalars.count` or show `NAME_TOO_LONG` |
| N5 backup | n/a | — | No DB backup exists, so cascades are irreversible (by intent) |
| N6 iOS 18+, VoiceOver, Dynamic Type, privacy | Yes | Collections live in their own tables. There is no path into `base_transcripts`, embeddings, search text, notifications or prompts. `CollectionName.toString()` is redacted; error messages are fixed strings; `ON CONFLICT` avoids Postgres `DETAIL` lines with the name. iOS: labels and `.font(.body)`-style text styles on the three screens. | Rename backstop (23505) would make Postgres log a `DETAIL` with the name; it only happens if the lock is bypassed |

## 11. Red-flag screen

- **Shallow module:** no. Callers make one request per user action; ordering, renumbering, locking and limits sit behind 8 service methods.
- **Information leakage:** none on the wire (no sort key, page number or version). `name_key` stays inside `CollectionName` and the repository.
- **Temporal decomposition:** none. One service owns the collection invariants across create, add and move.
- **Pass-through:** the controller adds auth, clamping and `Placement` parsing. The service adds policy. The repository is SQL. Three files from request to row.

## 12. Spikes on real Postgres 15 (riskiest assumptions first)

**S1 — scale and contention.** Riskiest assumption: derived counts and row locks are fast enough at the limits.

Setup: `postgres:15` with V6, V24 and V28-equivalent tables only. Seed one user with 5,000 `user_transcripts`, and 200 collections × 5,000 items (1,000,000 rows) via `generate_series`. Run `VACUUM ANALYZE`, then repeat the measurements without the vacuum to see the stale-visibility-map case. Measure with `pgbench -f` custom scripts and `EXPLAIN (ANALYZE, BUFFERS)`.

| Check | Measurement | Pass bar |
|---|---|---|
| (a) list with counts | p90 of 200 runs of the `listOwned` SQL | **< 150 ms** with vacuum; < 300 ms without |
| (b) move | p90 of the 4-probe + 1-update script on a 5,000 collection; `n_tup_upd` delta per move | p90 **< 20 ms**; delta = 1 |
| (b) renumber | p99 of renumber on 5,000 | **< 150 ms** |
| (c) contention | 16 clients add distinct transcripts to one collection seeded at 4,990 | final count **exactly 5,000**; only `COLLECTION_FULL` failures; lock-wait p90 **< 50 ms** |
| (d) deep keyset page | `pageIds` with the cursor at item 4,950 | p90 **< 10 ms** |
| (e) cascades | bulk-delete 100 transcripts that are each in all 200 collections (20,000 cascade rows); account delete of 1,000,000 items | **< 1 s**; **< 10 s** and success |

Any failure of (a) switches to a stored `item_count` maintained in the same statements, plus explicit decrements in the two delete paths.

**S2 — test-schema parity.** Riskiest assumption: Hibernate 6.6 `ddl-auto=create` emits what the tests rely on. Boot the test profile with the two entities on `postgres:15-alpine`, then query `pg_constraint`.
- Pass: `confdeltype='c'` on all three FKs, the unique `(user_id,name_key)` and the CHECK all present.
- Then run the R10 cascade via `TranscriptService.deleteTranscripts`. Pass: memberships gone, no exception.
- If `@OnDelete` isn't emitted, R10 needs an explicit `collectionItemRepository.deleteByUserTranscriptIds(ids)` before `deleteAll`.

**S3 — page endpoint end to end.** Hit `GET …/transcripts?size=50` on a 5,000 collection with realistic transcript text through the running app. Pass: p90 **< 500 ms** and body size recorded. This also tells us whether to memoise alias lookups.

## 13. What this design obliges us to build

Backend
- B1 `V28__create_collections.sql` (§4.1).
- B2 `CollectionEntity`, `CollectionItemEntity`, `CollectionItemId` with `@UniqueConstraint`, `@OnDelete`, `@Check`, `@Index`.
- B3 `CollectionName`, `Placement`, `SortKeys`, `CollectionSummary`, `CollectionError`, `CollectionException`.
- B4 `CollectionRepository`, `CollectionItemRepository` and projections `CollectionRow`, `KeyedItem`.
- B5 `CollectionService` (8 operations, lock timeout, SQLState translation).
- B6 `CollectionController` + DTOs + request size clamp.
- B7 `ErrorResponse.code` + `GlobalExceptionHandler` handler that never logs input.
- B8 `UserService.deleteAccount` first line `collectionRepository.deleteAllOwnedBy`.
- B9 per-request alias memo in the page mapping, if S3 says so.

iOS
- I1 `CollectionService`: `CollectionError`, error-body decoding, new `moveTranscript`, `after` paging, `initialTranscriptId`, drop `description`.
- I2 `HTTPError.notFound/.conflict` carry `Data?`; update the one external use.
- I3 `CollectionResponse` drops `description`; new `CollectionTranscriptPage` model + mapper.
- I4 `CollectionDetailViewModel`: confirmed snapshot, serial move queue, cursor paging, in-flight guard, cursor-gone reload, de-duplication by ID.
- I5 `CollectionPickerViewModel`: typed error messages; "New collection" with `initialTranscriptId`.
- I6 `ScoopApp.swift:43-50` configure `CollectionService`; restore the Home entry (A–Z list, counts, empty state, confirm-delete-if-not-empty).
- I7 offline gating on Collections screens and picker entry points (`OfflineUnavailableView`).
- I8 VoiceOver labels and Dynamic Type text styles on the three Collections screens; name validation by `unicodeScalars.count`.
- I9 update `CollectionServiceTests`, `CollectionDetailViewModelTests`, `CollectionPickerViewModelTests`, `CollectionResponseTests` to the new contract.

Migration
- M1 V28 on prod Postgres 15. New tables only, no backfill, no lock on existing tables beyond FK creation on `users`/`user_transcripts` (brief `SHARE ROW EXCLUSIVE`) [I].

Tests
- T1 `SortKeysTest`, `CollectionNameTest`.
- T2 `CollectionServiceConcurrencyIT`.
- T3 `CollectionControllerIT` (every code).
- T4 `CollectionOrderingIT`.
- T5 `CollectionsMigrationParityIT` (Flyway on, pgvector image).
- T6 R10/R11 integration + `UserServiceTest` update.
- T7 N6 log-capture test.

Ops
- O1 confirm the prod Postgres `log_min_error_statement` setting and that app log shipping excludes request bodies.
- O2 watch `collection_items` bloat and autovacuum after renumbers (`pg_stat_user_tables.n_dead_tup`).
- O3 run spikes S1–S3 before Phase D.

## 14. Tradeoffs accepted

- We accept the anchor-based move contract, which can't express "apply this whole order", in exchange for constant payload and 1-row writes. A future "sort A–Z" would need its own endpoint.
- We accept sort-key uniqueness that holds by construction under a lock, not by a constraint, in exchange for a single-statement renumber and a schema the tests can see.
- We accept derived counts, which cost a count per collection on every list, in exchange for R10/R11 needing no code in the delete paths. S1(a) decides.
- We accept cross-person membership being impossible only by SQL (`insertOwned` filters by `ut.user_id`), not by composite FKs, in exchange for no new unique index on the heavily used `user_transcripts`.
- We accept serialising each person's create/rename on their `users` row, in exchange for race-free 200-limit and name checks without SERIALIZABLE retries. Other writers to the row wait at most for one short transaction.
- We accept that a row another device moves across the paging cursor may be duplicated or missed (client de-duplicates; refresh corrects), in exchange for keyset paging that's exact for this device's own edits.
- We accept case-folding by Java `Locale.ROOT` (`"ß"` ≠ `"SS"`) as the definition of "ignoring case".

## 15. Alternatives considered

**A. Full-list replacement with dense integer positions (keep `PATCH /order {userTranscriptIds}`).**
- The client sends the complete new order, and the server rewrites `position = index`.
- With 50 of 5,000 loaded, the client must either first fetch an ID index of all 5,000 (a new endpoint, ≈200 KB down and ≈200 KB up per drag), or send a partial list with server-side splice rules. The June design started the splice at the minimum current position, which is ambiguous when another device adds or removes.
- Each drag writes up to 5,000 rows.
- It needs a version/precondition to reject stale lists, which puts a storage concern (version) on the wire.
- It hides less (the client must know the whole collection) and exposes more (ID index, version, partial-list rules).
- It lost on payload (~4,000× larger), write amplification and caller complexity.

**B. Order as a `uuid[]` on the collection row (the array is the membership, no join table).**
- Reorder is one row write and the limit is `array_length` under the row lock.
- There's no FK from array elements, so R10 needs hand-written `array_remove` in `TranscriptService.deleteTranscripts` and in account delete. That sync must happen in two places and is invisible to the cascade tests.
- Every drag rewrites an ≈80 KB TOASTed array (5,000 × 16 B).
- `contains` needs a GIN index, and paging is `unnest … WITH ORDINALITY`.
- It hides ordering well but leaks deletion responsibility into the transcript module (information leakage).
- It lost on R10 coupling and write amplification.

Not considered whole-shape: fractional string keys (LexoRank). They're a variant of this sparse-key shape that avoids renumbering at the cost of unbounded key growth.

## 16. Open questions and risks

- Does the multi-select bar ever add **several** transcripts at once (R6 says one transcript; the dock passes a set, `FeedScreen.swift:298-306`)? If yes, should `POST /{id}/transcripts` accept `userTranscriptIds[≤100]`, all-or-nothing, with 422 if the batch would exceed 5,000?
- Is a 503 `COLLECTION_TRY_AGAIN` on a 3 s lock timeout acceptable as R12's "failed save", or should the lock wait longer?
- Should `updatedAt` move on membership changes or only on rename? (This sketch: rename only.)
- Is it acceptable that account delete of a maximal account (1M memberships) runs as one long transaction? S1(e) gives the number.

## 17. Synthesis decision

*Filled in by arena.*

## 18. Next implementation step

Run spike S2, then S1. Then write `V28__create_collections.sql`, the two entities, and `CollectionsMigrationParityIT`, so the schema the tests use is proven equal to the schema Flyway ships before any service code is written.
