# Candidate 2 — Collections design

Phase A/B candidate, 5 October 2026. Design only; no repository changes, migration execution, runtime benchmarks, or test runs. Phase checklist: Ground complete; Sketch complete; Agree belongs to the orchestrator; Implement excluded; Scrap if the proposed spikes break the shape. No earlier-run candidate files were opened, read, searched, or used.

Source prefixes below: **B** = `/Users/apushpavannan/personal/content-categorise`; **I** = `/Users/apushpavannan/personal/TranscribeAssistant-ios`. Both checked-out branches are `main`, at `86f6dc5c35a66be29608c8b25b016d9bc423d713` and `9e22533721cadc09c9b8af63775f2ee5ff99f292`. All repository citations refer to these main snapshots. The shared `/tmp/arena-collections/grounding.md` is the authority for R1–R13 and N1–N6. No excluded branches, historical implementations, or untracked repository documents informed this package.

Evidence labels: **measured** includes direct source inspection or the explicitly identified JSON serialization measurement, not an executed application claim. **Inferred** means a conclusion from cited source or the proposed SQL/transaction shape. **Guess** means an unverified product/performance assumption. New behavior is a **design decision**, with its runtime guarantees still inferred until the named tests/spikes run. Unmarked source references below are measured source observations; no source citation is offered as runtime proof.

## Usage (caller's view)

Use Collections as a private, online-only organizer of **saved user transcripts**. The iOS service accepts domain values and returns domain summaries, pages, or typed failures; JSON, HTTP status decoding, cursors, and dates stay inside the service. It uses the existing authenticated `HTTPClient`. Configure it beside the other services in `ScoopApp` and restore a Collections entry on Home. The current setup configures other services but omits Collections (**measured, source inspection**: `I/Scoop/ScoopApp.swift:43–50`).

The following three call sites are proposed usage sketches, not Swift implementation. `CollectionID` and `UserTranscriptID` wrap UUIDs; `CollectionRevision` wraps an Int64. `Collections` below is the existing CollectionService evolved to these operations, not another network layer.

### Home and collection browsing

```swift
// DashboardScreen / CollectionsViewModel: server order is authoritative.
let summaries = try await collections.list()
home.collections = summaries                         // A–Z, counts, revision
home.emptyMessage = summaries.isEmpty ? "No collections yet" : nil

// CollectionDetailViewModel: one fetch in flight; one page generation at a time.
let first = try await collections.page(collectionID, after: nil, limit: 50)
detail.replaceWithSavedPage(first)
if let cursor = detail.nextCursor {
    let next = try await collections.page(collectionID, after: cursor, limit: 50)
    detail.appendIfSameGeneration(next)
}

// CollectionDetailScreen: a summary row navigates by the saved transcript UUID.
navigationPath.append(item.userTranscriptID)
// TranscribeDetailsScreen then uses the existing /api/v1/transcript/{id} path.
```

Use a small collection row model containing the saved transcript ID, display title, creator, platform, category display text, and duration. Do not fake a full `Transcript` by putting an empty string into its required transcript field. The existing collection screen passes full `Transcript` objects into `TranscriptCard` (`I/Scoop/views/Collections/CollectionDetailScreen.swift:29–34`), and the existing full response includes transcript and structured content (`I/Scoop/models/api/TranscriptResponse.swift:3–24`). **Inferred:** a collection-specific summary row avoids unbounded transcript payloads and per-item alias mapping. Detail already accepts a UUID (`I/Scoop/views/TranscriptDetails/TranscribeDetailsScreen.swift:5–7`); the backend already exposes owned detail (`B/src/main/java/com/app/categorise/api/controller/TranscriptController.java:95–103`).

### Feed/category/search selection and detail picker

```swift
// FeedScreen's selection dock; detail uses the same call with a singleton.
let pickedIDs: [UserTranscriptID] = feed.selectedIDsInVisibleOrder
let initial = try await collections.picker(for: pickedIDs)
picker.show(initial)                                 // none / some / all

// Optional "New collection" inside the picker.
let created = try await collections.create(name: userEnteredName)
picker.include(created)                              // empty until Save succeeds

// Save ONE delta, rather than coordinating per-transcript HTTP requests.
do {
    let saved = try await collections.changeMemberships(
        transcripts: pickedIDs,
        addTo: picker.explicitlyCheckedIDs,
        removeFrom: picker.explicitlyUncheckedIDs
    )
    picker.accept(saved)
    feed.endSelection()
} catch {
    picker.restoreInitialSelectionAndShow(error.userMessage)
    picker.refreshWhenOnline()                       // reconcile uncertain saves
}

// Transcript detail: list only rows with membership == .all from a singleton
// picker read, and offer the same picker to change them.
```

For several selected transcripts, `all` is checked, `none` unchecked, and `some` an accessible mixed state. Tapping mixed means add all; explicitly clearing a checked/mixed row means remove all selected transcripts from that collection. Untouched rows produce no delta, so another device's unrelated changes survive. A create is its own atomic request; if subsequent membership Save fails, the newly created empty collection remains visible. That is a saved state, not a partial membership write.

**Measured, source inspection:** the present picker takes one UUID (`I/Scoop/views/Feed/CollectionPickerSheet.swift:3–11`), derives ticks from `contains` (`I/Scoop/viewModel/CollectionPickerViewModel.swift:20–26`), and changes each tick immediately (`I/Scoop/viewModel/CollectionPickerViewModel.swift:33–50`). The selection dock currently exposes categorize/delete/share actions (`I/Scoop/views/Feed/FeedScreen.swift:298–306,407–414`). This design adds its Collections action for the general feed, category feeds, and search results and changes the picker to explicit atomic Save.

### Reorder while only a prefix is loaded

```swift
// CollectionDetailViewModel.move: derive identities from the saved list.
let saved = detail.savedState
let moved = saved.ids(at: sourceOffsets)              // order from saved rows
let preview = saved.moving(sourceOffsets, to: destination)
let before = preview.firstUnmovedIDAfterMovedBlock
    ?? saved.firstUnloadedID                         // prefix end != global end
detail.showPending(preview)

do {
    let canonicalFirstPage = try await collections.move(
        collectionID, transcripts: moved, before: before,
        expectedRevision: saved.revision
    )
    detail.replaceWithSavedPage(canonicalFirstPage)   // discard every old cursor
} catch {
    detail.restore(saved)
    detail.show(error.userMessage)
    detail.reloadSavedFirstPageWhenOnline()
}

// An explicit "Move to end" uses before: nil. "Move to beginning" uses the
// current first unmoved item. A destination further away is chosen by paging
// to an anchor, retaining the selected source identities and revision.
```

The last-item boundary matters: dragging item 10 to the end of a loaded 50-item prefix means **before item 51**, not after item 5,000. Every page supplies the first unread UUID from its lookahead row. When no unread item exists, `nil` means global end. VoiceOver offers move before, move to beginning, and move to end actions; pointer drag is not the only way to reorder. A changed revision cancels a distant destination selection and reloads it.

## Problem

Collections must combine private ownership, case-insensitive names, hard cardinality limits, atomic multi-selection writes, consistent paged ordering, and transcript/account deletion. The current iOS slice assumes missing backend endpoints and sends only its loaded IDs as a replacement order; it ignores the reorder result and has no load-more guard (**measured, source inspection**: `I/Scoop/service/CollectionService.swift:139–165`; `I/Scoop/viewModel/CollectionDetailViewModel.swift:34–52`). The existing service-level IgnoreCase check is not a concurrency guard (`B/src/main/java/com/app/categorise/domain/service/UserSubcategoryService.java:81–87`). The design therefore needs a different reorder/paging contract and database-backed invariants, while retaining the recognizable list/create/rename and singleton membership paths.

### Grounded integration model

The owned saved row is `user_transcripts`, pointing at shared `base_transcripts`; collection membership must point at that owned row, not the shared content. A principal reaches a controller, which derives the owner and calls a domain service, which queries the owned saved row and maps content for the response. Existing optional feed filters use Criteria API and offset/count queries. Collection ordering needs its own storage query rather than adding a predicate after the existing page query.

| Label | Main-branch evidence | Design implication |
|---|---|---|
| Measured | `B/src/main/resources/db/migration/V6__replace_transcripts_with_new_schema.sql:8–44` separates bases and saved rows and cascades saved-row owner/base FKs. | New composite saved-row membership FK preserves personal ownership and shared-content independence. |
| Measured | `B/src/main/java/com/app/categorise/api/controller/TranscriptController.java:95–103,176–180` derives the principal owner and exposes saved-row detail. | Collections navigation needs only saved UUID; every new endpoint derives the owner identically. |
| Measured | `B/src/main/java/com/app/categorise/data/repository/UserTranscriptRepositoryImpl.java:38–74` performs offset read, separate count, and creation-time ordering. | Do not reuse it for ordered generation-consistent collection pages. |
| Measured | `B/src/main/java/com/app/categorise/application/mapper/VideoMapper.java:80–101,108–118` includes full content and can query aliases per item. | Summary projection joins display metadata directly and avoids per-item mapper calls. |
| Measured | `I/Scoop/viewModel/CollectionDetailViewModel.swift:34–52` pages independently and ignores reorder failure. | Add a generation/in-flight guard and explicit restore/reload state transition. |
| Inferred | `B/src/main/java/com/app/categorise/domain/service/TranscriptService.java:233–268` schedules deletes in an outer transaction. | FK/projection proof must include flush/commit, not merely return from the local method body. |
| Inferred | `B/src/main/java/com/app/categorise/exception/GlobalExceptionHandler.java:119–133` handles uncategorized failures with500/raw message. | Catch anticipated constraint/lock failures outside the transaction and before this fallback. |

The shared grounding supplies the earlier rationale and deployment constraints; this candidate does not revisit excluded feature history. The storage boundary is introduced only for Collections; existing transcript search, embedding, categorization and notification ownership stay with their current services.

## Shape

### Data structures first

Use an owned `collections` table and an owned `collection_memberships` join table referencing `user_transcripts`. Store **sparse numeric ranks** for display order and a separate, private **bounded capacity slot** for cardinality. Neither rank nor slot reaches iOS.

| Structure | Invariant / purpose |
|---|---|
| Collection capacity slot in 1…200, unique per owner | There cannot be a 201st collection, even if a check is bypassed. Slots are not alphabetical or display order. |
| Normalized name plus generated database `lower(name)` key, unique per owner | The database owns duplicate detection. The same key supplies list ordering; client sorting cannot redefine it. |
| Membership capacity slot in 1…5,000, unique per collection | There cannot be a 5,001st membership. Removing one frees a slot without disturbing display order. |
| Membership primary key `(collection_id, user_transcript_id)` | Re-add is a no-op. Slot allocation happens only for actually missing pairs. |
| Rank `NUMERIC(38,18)`, unique per collection, deferrable | A move usually changes only moved rows. A bounded server-side rebalance handles exhausted gaps. |
| Composite membership foreign keys including `user_id` | A collection cannot contain another person's saved transcript, including through a programming mistake. |
| `transcript_count`, `revision`, `updated_at` on collection | Database-maintained read projections. Membership DML/cascades update these in the same transaction; application code never also adjusts them. |

Membership rows are the source of truth. Count is a materialized projection, not an independent service-maintained invariant; capacity is enforced by slots rather than trusting that projection. Revision is a monotonic invalidation token, not an operation counter or user-visible position. It changes for membership/order changes and renames. Empty/no-op membership operations leave it unchanged. A bulk transcript deletion may increment it more than once if Hibernate emits several deletes; its exact increment is deliberately unspecified.

**Design decision:** normalize names to NFC, trim the Unicode White_Space characters enumerated in the SQL below, and count Unicode code points after normalization. Case-insensitive equality and A–Z sorting use PostgreSQL `lower` under the deployed database locale, with UUID as final tiebreak. Do not strip accents. iOS displays the server's canonical name and server order. **Guess:** this locale policy matches the product's non-English expectations; the name-policy spike is required before accepting it. This is an explicit default, not an unimplemented naming decision.

The service boundary is one `CollectionService`; it owns transactions, authorization, atomic membership deltas, optimistic reorder checks, and typed failures. A collection-specific repository owns native SQL, capacity allocation, rank calculations, and projections. Controllers adapt wire DTOs and `UserPrincipal.getId()` into domain values. This mirrors the current controller/service/repository placement and native-SQL precedent without adding a generic lock framework or ordering platform.

Per `boundary-discipline`, parse transport into domain types before the service. Per `encode-lessons-in-structure`, typed IDs, names, revisions, disjoint deltas, and database constraints carry the invariants. Per `single-source-of-truth`, ranks and capacity slots are private storage decisions. Per `make-operations-idempotent`, add/remove express desired transitions. Per `minimize-reader-load`, the normal trace is controller → service → repository; database projection triggers are the one explicit lifecycle boundary needed for cascades.

### Dominant reads and writes

| Access pattern | Trace / cost shape |
|---|---|
| Home list | Owned index by `(user_id,name_key,id)` reads at most 200 collection rows, including stored counts; no scan of all memberships. |
| Singleton picker / R8 | Verify the saved transcript belongs to the principal, then left join its membership pairs to the owned collection list. Foreign/missing transcript is 404 even when the owner has no collections. |
| Multiple-selection picker | Validate selected owned IDs, aggregate matched membership counts through the reverse membership index, and join onto at most 200 summaries. No HTTP loop per transcript. |
| Page | Read one collection revision and at most `limit + 1` membership summaries through the rank index, in one short repeatable-read transaction. Join source title/creator/category data once. |
| Append / remove | Lock affected transcript rows, then collection rows in UUID order; find genuinely missing/present pairs; validate every target before any DML; issue set-based insert/delete. Projection triggers update each affected collection per statement. |
| Reorder | Lock only this collection after the account read gate; check expected revision; resolve members/anchor; allocate ranks or rebalance; one set-based rank update; return the canonical first page. |
| Transcript delete | Own the saved transcript rows, then affected collections; DB cascade deletes memberships and advances count/revision. No shared base-transcript deletion. |

Collection summary fields are `id,name,description:null,transcriptCount,createdAt,updatedAt,revision,contains?`. The optional legacy `description` is always null and has no database field. A collection-page item is `userTranscriptId,title,account,platform,categoryDisplayName,duration`; title prefers generated title, then source title, then a stable fallback and is capped at 200 code points for this row response; creator/category display strings are also capped at 200. Complete content is fetched only on detail navigation. Collection data is never appended to a transcript DTO.

### Explicit decision on register row 6

**Replace parts of the assumed API.** Keep `GET /collections` as an unpaged array, `GET /collections?transcriptId=…` with `contains`, name create/rename, owned collection delete, and singleton membership routes. Replace the collection page response and order request; add an atomic membership-delta endpoint and a multi-selection picker endpoint. Remove description editing. The backend does not currently implement these paths, so there is no deployed backend compatibility contract to preserve in this slice (shared grounding; service assumptions at `I/Scoop/service/CollectionService.swift:4–7,23–43,139–165`).

Keeping the old prefix-list reorder would require loading the full collection or inventing ambiguous partial-list behavior. Keeping offset pages without a revision would allow duplicates/missing rows after movement. Keeping full transcript payloads and per-transcript picker writes would make the latency and atomicity requirements harder to meet. Additive fields preserve the useful existing response shape; the Collections UI/service tests must explicitly change to the new page/move contract. Do not ship the Home entry before the backend and the updated iOS service are both available.

## Reorder and paging contract

```http
GET /api/v1/collections/{collectionId}/transcripts?size=50

200
{
  "collection": {"id":"…","name":"Recipes","transcriptCount":5000,
                 "createdAt":"…","updatedAt":"…","revision":42,"description":null},
  "items": [{"userTranscriptId":"…","title":"…","account":null,
             "platform":"YOUTUBE","categoryDisplayName":"Recipe","duration":120}],
  "nextCursor":"opaque",
  "firstUnloadedUserTranscriptId":"22222222-2222-2222-2222-222222222222"
}

GET /api/v1/collections/{collectionId}/transcripts?size=50&cursor=opaque

PATCH /api/v1/collections/{collectionId}/order
Content-Type: application/json

{"expectedRevision":42,"movedUserTranscriptIds":["11111111-1111-1111-1111-111111111111"],"beforeUserTranscriptId":"22222222-2222-2222-2222-222222222222"}
```

The moved IDs form an ordered block: remove them from the saved order, preserve every other member's relative order, and insert the block before the anchor; null anchor means global tail. Reject empty/repeated moved IDs, an anchor inside the block, or malformed UUIDs with 400 `INVALID_REORDER`. At most 5,000 moved IDs are possible; a larger block is invalid, not silently truncated. A moved UUID or anchor not currently in this owned collection returns 404 `COLLECTION_MEMBER_NOT_FOUND`. A missing/foreign collection returns 404 `COLLECTION_NOT_FOUND`. If the expected revision is stale, return 409 `COLLECTION_CHANGED` before evaluating stale member identities, after checking collection ownership.

The existing `userTranscriptIds` replacement-order body is explicitly rejected with 400 `INVALID_REORDER`; it must never be interpreted as a loaded-prefix replacement. A client can send all 5,000 distinct current IDs with a null anchor for a complete permutation, but a normal drag needs only moved identities and one anchor.

Rank allocation reads predecessor/successor ranks after removing the moved block logically. First compare the final identity sequence to the saved sequence: a semantic no-op returns the saved first page without changing ranks or revision. With a gap, distribute `k` new ranks at equal intervals using exact BigDecimal arithmetic at scale 18; verify strict monotonicity after rounding. At either end, use spacing 1,024. If finite precision or the numeric bound leaves insufficient room, compute the final entire order on the server and assign `row_number * 1024`. Defer the rank-uniqueness constraint for this transaction and update only rows whose final rank differs in **one** statement. Force the constraint immediate before return. Neither fallback nor movement changes capacity slots. No rebalance worker, cache, or cleanup job is needed.

For this sketch, planning may read the entire bounded order as at most5,000 UUID/rank pairs even for an ordinary move; it never loads5,000 full transcripts. Planning is O(n+k) in memory/work, then one parameterized set-based UPDATE of changed ranks and the first-page summary read. The client payload and normal row writes remain small. The spike measures this concrete straightforward plan; an unbuilt neighbor-only optimization is not credited toward the latency target.

| Operation | Membership rows written | Collection projection rows written | Request JSON bytes |
|---|---:|---:|---:|
| One moved item before an anchor, sufficient gap | 1, or 0 for a true no-op | 1, or 0 | 153 in the exact example above |
| One moved item to global end | 1, or 0 | 1, or 0 | 119 at revision 42 |
| `k` moved items, sufficient rank gap | At most `k` changed rows | At most 1 | About `39k` plus fixed fields |
| Exhausted-gap rebalance, collection has 5,000 | At most 5,000 changed rows | At most 1 | Same small request for a single drag |
| Complete permutation of 5,000 UUIDs | At most 5,000 changed rows | At most 1 | 195,080 with compact JSON, null anchor, revision 42 |

**Measured:** compact ASCII JSON serialization in this runner gives the byte counts above; the large-payload fixture used repeated UUID strings solely for length, not as a valid move. All canonical UUID strings have equal length, so a distinct-ID fixture has that length. HTTP headers, TLS, escaping/whitespace chosen by Swift's encoder, and response bytes are excluded. The grounding separately reports approximately 200 KB for a 5,000-ID reorder. **Inferred:** row-write bounds follow the proposed set-based update and statement trigger; physical WAL/index writes exceed logical row counts. **Guess / acceptance target:** p90 below 500 ms for a normal move and for the forced 5,000-member rebalance, measured from server request arrival through response completion on the one-server deployment shape. No latency was measured here.

Cursor contents are opaque, versioned, authenticated data: owner UUID, collection UUID, collection revision, page limit, last emitted rank, last emitted member UUID, and cursor format version. Encode them as an authenticated token with a deployment secret; ranks never become caller parameters. Verify token format/MAC, owner, collection and limit before using its position. A token from another owner/collection is an ownership-safe 404. Malformed token or changed limit is 400 `INVALID_CURSOR`.

Within each page read use `REPEATABLE READ, READ ONLY`; fetch the collection row first, compare cursor revision, then query `(rank,user_transcript_id) > (:lastRank,:lastId) ORDER BY rank,user_transcript_id LIMIT size+1`. First-page reads have no lower bound. Return the first `size` rows and the extra row's UUID as `firstUnloadedUserTranscriptId`; the next cursor is based on the last emitted row. No next cursor/lookahead UUID when finished. `size` defaults to 50 and is clamped to 1…100, matching the existing page-size convention (`B/src/main/java/com/app/categorise/api/controller/TranscriptController.java:71–84`).

**Inferred consistency guarantee:** each accepted page has one membership/order generation. If a write commits between pages, the next page returns 409 `COLLECTION_CHANGED`, never silently mixes generations. A write that commits during a page cannot make that page internally inconsistent because both reads share a snapshot. A GET that began before a move may finish with the old generation; the iOS page-generation token discards it after the move response. Successful move returns the canonical first page at the new revision in the mutation transaction; iOS discards all old pages/cursors and replaces its rows. There is no promise of retaining an old snapshot across requests. This deliberate invalidation is how paging stays consistent after reorder, append, removal, and R10 cascade deletion.

Disable load-more, further drags, and conflicting edits while a move is pending. On a rejected move restore the captured saved list, show a typed message, and then reload canonical state. On connection loss the commit outcome is unknown: restore the last acknowledged list and show “Couldn't confirm the save”; reconcile from the server on reconnect. Do not label it authoritative current state or replay a stale move blindly. A retry of a committed move receives a stale-revision conflict rather than applying it twice.

## Flyway SQL sketch

Proposed `V28__create_collections.sql`, not executed. Keep all existing migrations unchanged. An additional unique key on the existing saved-transcript table enables a composite ownership FK; it is a deliberate integration change, not a change to transcript content. All new collection data is owner-local.

```sql
CREATE FUNCTION collections_normalize_name(raw_name text)
RETURNS text LANGUAGE sql IMMUTABLE STRICT AS $$
  SELECT normalize(btrim(raw_name,
    E' \t\n\r\f' || chr(11) ||
    U&'\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'), NFC)
$$;

ALTER TABLE user_transcripts
  ADD CONSTRAINT user_transcripts_id_owner_uq UNIQUE (id, user_id);

CREATE TABLE collections (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL,
  capacity_slot smallint NOT NULL,
  name varchar(100) NOT NULL,
  name_key text GENERATED ALWAYS AS (lower(name)) STORED,
  transcript_count integer NOT NULL DEFAULT 0,
  revision bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT collections_owner_fk FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT collections_id_owner_uq UNIQUE (id, user_id),
  CONSTRAINT collections_slot_ck CHECK (capacity_slot BETWEEN 1 AND 200),
  CONSTRAINT collections_owner_slot_uq UNIQUE (user_id, capacity_slot),
  CONSTRAINT collections_name_ck CHECK (
    char_length(name) BETWEEN 1 AND 100
    AND name = collections_normalize_name(name)),
  CONSTRAINT collections_owner_name_uq UNIQUE (user_id, name_key),
  CONSTRAINT collections_count_ck CHECK (transcript_count BETWEEN 0 AND 5000),
  CONSTRAINT collections_revision_ck CHECK (revision >= 0)
);
CREATE INDEX collections_owner_name_idx ON collections(user_id, name_key, id);

CREATE TABLE collection_memberships (
  collection_id uuid NOT NULL,
  user_id uuid NOT NULL,
  user_transcript_id uuid NOT NULL,
  capacity_slot smallint NOT NULL,
  rank numeric(38,18) NOT NULL,
  added_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT collections_members_pk PRIMARY KEY (collection_id, user_transcript_id),
  CONSTRAINT collections_members_collection_fk FOREIGN KEY (collection_id, user_id)
    REFERENCES collections(id, user_id) ON DELETE CASCADE,
  CONSTRAINT collections_members_transcript_fk FOREIGN KEY (user_transcript_id, user_id)
    REFERENCES user_transcripts(id, user_id) ON DELETE CASCADE,
  CONSTRAINT collections_members_slot_ck CHECK (capacity_slot BETWEEN 1 AND 5000),
  CONSTRAINT collections_members_slot_uq UNIQUE (collection_id, capacity_slot),
  CONSTRAINT collections_members_rank_uq UNIQUE (collection_id, rank)
    DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX collections_members_page_idx
  ON collection_memberships(collection_id, rank, user_transcript_id);
CREATE INDEX collections_members_transcript_idx
  ON collection_memberships(user_id, user_transcript_id, collection_id);

-- One projection update per affected collection per INSERT statement.
CREATE FUNCTION collections_members_inserted() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE d record;
BEGIN
  FOR d IN SELECT collection_id, count(*) AS n FROM inserted_members
           GROUP BY collection_id ORDER BY collection_id LOOP
    UPDATE collections SET transcript_count = transcript_count + d.n,
      revision = revision + 1, updated_at = clock_timestamp()
      WHERE id = d.collection_id;
  END LOOP;
  RETURN NULL;
END $$;
CREATE TRIGGER collections_members_insert_projection
AFTER INSERT ON collection_memberships
REFERENCING NEW TABLE AS inserted_members FOR EACH STATEMENT
EXECUTE FUNCTION collections_members_inserted();

-- This also runs for FK cascades. A parent already being deleted needs no update.
CREATE FUNCTION collections_members_deleted() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE d record;
BEGIN
  FOR d IN SELECT collection_id, count(*) AS n FROM deleted_members
           GROUP BY collection_id ORDER BY collection_id LOOP
    UPDATE collections SET transcript_count = transcript_count - d.n,
      revision = revision + 1, updated_at = clock_timestamp()
      WHERE id = d.collection_id;
  END LOOP;
  RETURN NULL;
END $$;
CREATE TRIGGER collections_members_delete_projection
AFTER DELETE ON collection_memberships
REFERENCING OLD TABLE AS deleted_members FOR EACH STATEMENT
EXECUTE FUNCTION collections_members_deleted();

-- Membership identity and capacity slot are immutable. Only rank is updated.
CREATE FUNCTION collections_members_identity_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.collection_id, NEW.user_id, NEW.user_transcript_id, NEW.capacity_slot)
     IS DISTINCT FROM
     (OLD.collection_id, OLD.user_id, OLD.user_transcript_id, OLD.capacity_slot) THEN
    RAISE EXCEPTION 'membership identity is immutable'
      USING ERRCODE = '23514', CONSTRAINT = 'collections_members_identity_ck';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER collections_members_identity_guard
BEFORE UPDATE ON collection_memberships FOR EACH ROW
EXECUTE FUNCTION collections_members_identity_immutable();

CREATE FUNCTION collections_members_reordered() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE d record;
BEGIN
  FOR d IN
    SELECT DISTINCT n.collection_id FROM reordered_members n
    JOIN previous_members o USING (collection_id, user_transcript_id)
    WHERE n.rank IS DISTINCT FROM o.rank ORDER BY n.collection_id
  LOOP
    UPDATE collections SET revision = revision + 1, updated_at = clock_timestamp()
      WHERE id = d.collection_id;
  END LOOP;
  RETURN NULL;
END $$;
CREATE TRIGGER collections_members_update_projection
AFTER UPDATE ON collection_memberships
REFERENCING OLD TABLE AS previous_members NEW TABLE AS reordered_members
FOR EACH STATEMENT EXECUTE FUNCTION collections_members_reordered();
```

**Inferred:** a unique slot constrained to a finite range is a database proof of the hard limit, independent of isolation level. Choose the lowest available collection slot from `generate_series(1,200)` under the owner lock. For each collection choose enough free membership slots from `generate_series(1,5000)` under its lock; do not use `MAX(slot)+1`. This scan is bounded, and rank is independent of holes in slots. Verify NFC support with database encoding UTF8 in the migration fixture.

The SQL is a proposal with an explicit risk: PostgreSQL transition-table triggers must handle cascade batches and deleting their own collection parent as described. Their behavior is **inferred**, not runtime measured. Spike 1 below makes accepting this SQL contingent on that real lifecycle proof. The custom SQL does not require adding new dependencies: PostgreSQL driver and Flyway already exist (`B/pom.xml:85–91`).

## Atomicity and concurrency

Separate state by owner and by collection rather than serializing the server. Two actors can still write the same collection; the membership order/count are genuinely shared, so those writes must serialize at a small boundary. This is the explicit exception to the runner's default of per-actor state: independently merging ranks, names, or capacity permits outcomes the requirements forbid.

Use READ COMMITTED for writes. Every relevant transaction follows this lock order:

1. **Account gate first.** Existing `users` row scoped to the principal. Collection membership/reorder and transcript deletion take `FOR KEY SHARE`. Collection create/rename/delete and account deletion take `FOR UPDATE`. The latter is an owner-local catalog/account gate, not a global lock. Do not upgrade a previously acquired key-share gate to an update gate; select the required mode at entry.
2. **Saved transcripts next where needed.** Add/remove/picker Save takes `FOR KEY SHARE` for all selected `user_transcripts`, sorted by UUID. Transcript deletion takes `FOR UPDATE` for its entire owned set, sorted by UUID. Resolve an exact owned set; do not lock foreign rows. Reorder does not need transcript locks because it changes no FK identity.
3. **Collections last.** Acquire `FOR UPDATE` on every targeted owned collection, sorted by UUID; account/catalog exclusive gate makes their own collection deletes safe. Prelock every affected collection for transcript deletion, in that same order, before issuing deletes/cascades.

The transcript-before-collection order prevents the classic add/delete cycle: an add must obtain the saved-transcript key-share lock before it can hold a collection lock; a transcript delete cannot hold a transcript lock while waiting for a collection lock held by an add that is waiting for that transcript. Reorders only need a collection, so they cannot form the reverse edge. An exclusive account gate blocks new Collections writes while deletion removes its owned data. The small existing deletion services must join this protocol; SQL cascades alone do not prove the absence of lock cycles.

| Two-request case | Serialized outcome |
|---|---|
| Same name create / rename | Owner catalog gate serializes mutation; generated unique key is the final backstop. One succeeds; duplicate returns 409 `COLLECTION_NAME_DUPLICATE`. |
| Two creates starting with 199 collections | First occupies remaining slot; second finds none and returns 422 `COLLECTION_LIMIT`. Exactly 200 survive. |
| Two distinct adds starting with 4,999 members | Collection lock serializes set difference and free-slot allocation. First appends; second returns 422 `COLLECTION_TRANSCRIPT_LIMIT`. Exactly 5,000 survive. |
| Same pair added twice, including at capacity | First appends once; second has no missing pair and succeeds as a no-op. |
| Two moves with the same revision | First commits one new generation; second returns 409 `COLLECTION_CHANGED`. No rank overwrite. |
| Add vs transcript delete | Add finishes before delete and is cascaded away, or delete finishes first and add returns 404 `TRANSCRIPT_NOT_FOUND`. |
| Add vs collection delete | Add precedes the owner-exclusive delete, or sees 404 after it. No orphan. |
| Membership Save to several collections, one full/gone | Validate every owned target and resulting count under locks; fail the entire transaction. No successful-looking subset. |
| Name write vs account delete | The same owner update gate orders them. No insert can survive deletion of its FK owner. |

Free slot allocation and set differences happen after locks. Validate all names, owners, target identities, disjoint deltas, and final capacities before DML; constraints independently backstop ownership, names, and caps. Preserve input order when appending genuinely new transcripts. A no-op batch still verifies its referenced owned resources; absent membership removal is success, absent/foreign collection or transcript is 404. An empty delta is an owned read/no-op rather than inventing changes.

Writes run inside one **service-owned TransactionTemplate boundary**. Catch and translate SQL failures only after that transaction has rolled back, including failures during commit. Do not catch a uniqueness error then query inside the failed transaction. Do not use nested independent transactions for pieces of a picker Save. Existing `@Transactional` delete/account methods remain atomic; typed concurrency exceptions must escape the broad transcript-deletion catch unchanged, and their transaction-completion errors need the same safe outer mapping. That is scoped integration work, not a global transaction refactor.

Use a bounded lock wait; a lock timeout/deadlock/serialization conflict rolls back the whole write and maps to 409 `COLLECTION_WRITE_CONFLICT`, with a Retry message, rather than 500. Database unavailability/pool timeout maps to 503 `COLLECTION_SAVE_FAILED` for writes and `COLLECTION_UNAVAILABLE` for reads. Do not automatically replay stale moves. Set the exact lock-wait budget from the spike so contention can return a typed error without monopolizing the shared pool. **Guess:** short transactions suffice for the N2 load; the grounding's pool-of-10/job-poller constraint must be included in the latency spike.

## Java type and signature sketch

New types fit the existing `com.app.categorise` domain/data/api/exception packages. The snippets are intentionally non-executable sketches: imports, constructors for dependency injection, and repetitive accessors are omitted; every method body is `not implemented`. DTOs remain in `api.dto` and entities/ranks/capacity slots remain private to the repository. No public ORM object, ResponseEntity, JSON request, numeric rank, or cursor string enters the service API.

### Domain and validated inputs — `domain.model`

```java
public record CollectionId(UUID value) {}
public record UserTranscriptId(UUID value) {}
public record CollectionRevision(long value) {}

public final class CollectionName {
    private final String value;
    private CollectionName(String value) {
        throw new UnsupportedOperationException("not implemented");
    }
    public static CollectionName parse(String raw) {
        // TODO NFC; defined Unicode trim; nonblank; <=100 code points.
        throw new UnsupportedOperationException("not implemented");
    }
    public String value() {
        throw new UnsupportedOperationException("not implemented");
    }
}

public final class MembershipChange {
    // Ordered distinct transcripts; distinct disjoint add/remove collection sets.
    private final List<UserTranscriptId> transcripts;
    private final Set<CollectionId> addTo;
    private final Set<CollectionId> removeFrom;
    private MembershipChange(List<UserTranscriptId> transcripts,
                             Set<CollectionId> addTo, Set<CollectionId> removeFrom) {
        throw new UnsupportedOperationException("not implemented");
    }
    public static MembershipChange of(List<UserTranscriptId> transcripts,
                                      Set<CollectionId> addTo, Set<CollectionId> removeFrom) {
        throw new UnsupportedOperationException("not implemented");
    }
}

public final class CollectionMove {
    private final CollectionRevision expectedRevision;
    private final List<UserTranscriptId> moved;
    private final Optional<UserTranscriptId> before;
    private CollectionMove(CollectionRevision expectedRevision,
                           List<UserTranscriptId> moved, Optional<UserTranscriptId> before) {
        throw new UnsupportedOperationException("not implemented");
    }
    public static CollectionMove of(CollectionRevision expectedRevision,
                                    List<UserTranscriptId> moved,
                                    Optional<UserTranscriptId> before) {
        // TODO Distinct nonempty ordered block; anchor outside block; <=5000.
        throw new UnsupportedOperationException("not implemented");
    }
}

public enum MembershipState { NONE, SOME, ALL }
public record CollectionSummary(CollectionId id, CollectionName name, int transcriptCount,
                                CollectionRevision revision, Instant createdAt, Instant updatedAt) {}
public record CollectionPickerRow(CollectionSummary collection,
                                  MembershipState membership, int matchedTranscriptCount) {}
public record CollectionTranscriptSummary(UserTranscriptId id, String title,
                                          String account, String platform,
                                          String categoryDisplayName, double duration) {}

// Position is an opaque domain continuation; its concrete storage fields are
// package-private. The API controller's cursor codec handles wire encoding.
public final class CollectionPosition { /* private state; no public rank accessor */ }
public record CollectionPage(CollectionSummary collection, List<CollectionTranscriptSummary> items,
                             Optional<CollectionPosition> next,
                             Optional<UserTranscriptId> firstUnloaded) {}
public record MembershipReceipt(List<CollectionSummary> changedCollections) {}
public record DeleteCollection(CollectionRevision expectedRevision, boolean confirmNonEmpty) {}
```

The type factories enforce shape/length, not ownership. Ownership and revision depend on locked data. `CollectionRevision` must parse nonnegative values; ID wrappers reject null; page limit is normalized at the HTTP boundary. The implementation must make `CollectionPosition` construction private to the collection/cursor boundary so callers cannot invent rank state.

### Repository — `data.repository.CollectionRepository`

```java
@Repository
public class CollectionRepository {
    private final EntityManager entityManager;

    public void lockOwner(UUID userId, boolean exclusive) {
        throw new UnsupportedOperationException("not implemented");
    }
    public void lockOwnedTranscripts(UUID userId, List<UserTranscriptId> ids, boolean deleting) {
        throw new UnsupportedOperationException("not implemented");
    }
    public void lockOwnedCollections(UUID userId, Set<CollectionId> ids) {
        throw new UnsupportedOperationException("not implemented");
    }
    public List<CollectionSummary> listOwned(UUID userId) {
        throw new UnsupportedOperationException("not implemented");
    }
    public List<CollectionPickerRow> picker(UUID userId, List<UserTranscriptId> ids) {
        throw new UnsupportedOperationException("not implemented");
    }
    public CollectionSummary insertOwned(UUID userId, CollectionName name) {
        // TODO Allocate bounded free owner slot; INSERT RETURNING canonical summary.
        throw new UnsupportedOperationException("not implemented");
    }
    public CollectionSummary renameOwned(UUID userId, CollectionId id, CollectionName name) {
        throw new UnsupportedOperationException("not implemented");
    }
    public void deleteOwned(UUID userId, CollectionId id, DeleteCollection command) {
        throw new UnsupportedOperationException("not implemented");
    }
    public CollectionPage pageOwned(UUID userId, CollectionId id,
                                    Optional<CollectionPosition> after, int limit) {
        throw new UnsupportedOperationException("not implemented");
    }
    public MembershipReceipt applyOwned(UUID userId, MembershipChange change) {
        // TODO Under existing locks, plan final capacities for every target;
        // then delete and append missing pairs in set-based statements.
        throw new UnsupportedOperationException("not implemented");
    }
    public CollectionPage moveOwned(UUID userId, CollectionId id, CollectionMove move) {
        // TODO Compare revision; compute rank plan; one UPDATE of changed rows;
        // force deferred uniqueness; read canonical first page at limit 50.
        throw new UnsupportedOperationException("not implemented");
    }
    public void lockCollectionsForTranscriptDeletion(UUID userId, List<UserTranscriptId> ids) {
        throw new UnsupportedOperationException("not implemented");
    }
    public void deleteAllOwnedForAccount(UUID userId) {
        throw new UnsupportedOperationException("not implemented");
    }
    private RankPlan planRanks(List<StoredMember> savedOrder, CollectionMove move) {
        // TODO Pure exact numeric calculation; private storage types.
        throw new UnsupportedOperationException("not implemented");
    }
}
```

Repository SQL scopes every read/write by principal `user_id`; owning a collection is checked even if it is empty. The rank/slot policy belongs here because SQL constraints and stored precision constrain its calculations. Lock helpers are internal to the service/deletion integration, not separate caller-visible lifecycle steps.

### Service — `domain.service.CollectionService`

```java
@Service
public class CollectionService {
    private final CollectionRepository repository;
    private final TransactionTemplate writes;          // READ COMMITTED
    private final TransactionTemplate reads;           // REPEATABLE READ, read only

    public List<CollectionSummary> list(UUID userId) {
        throw new UnsupportedOperationException("not implemented");
    }
    public List<CollectionPickerRow> picker(UUID userId, List<UserTranscriptId> transcripts) {
        throw new UnsupportedOperationException("not implemented");
    }
    public CollectionSummary create(UUID userId, CollectionName name) {
        throw new UnsupportedOperationException("not implemented");
    }
    public CollectionSummary rename(UUID userId, CollectionId id, CollectionName name) {
        throw new UnsupportedOperationException("not implemented");
    }
    public void delete(UUID userId, CollectionId id, DeleteCollection command) {
        throw new UnsupportedOperationException("not implemented");
    }
    public CollectionPage page(UUID userId, CollectionId id,
                               Optional<CollectionPosition> after, int limit) {
        throw new UnsupportedOperationException("not implemented");
    }
    public MembershipReceipt changeMemberships(UUID userId, MembershipChange change) {
        // TODO One transaction: account gate, owned transcripts, owned collections,
        // all-target validation, DML, committed receipt; translate outside boundary.
        throw new UnsupportedOperationException("not implemented");
    }
    public CollectionPage move(UUID userId, CollectionId id, CollectionMove move) {
        throw new UnsupportedOperationException("not implemented");
    }
    private <T> T write(Supplier<T> action) {
        // TODO Catch named SQLSTATE/constraint failures only after writes.execute
        // has returned or rolled back; never expose SQL text or raw causes.
        throw new UnsupportedOperationException("not implemented");
    }
    private <T> T read(Supplier<T> action) {
        // TODO One repeatable-read snapshot; map unavailable persistence after
        // the read transaction ends to COLLECTION_UNAVAILABLE, never empty data.
        throw new UnsupportedOperationException("not implemented");
    }
}
```

The service is not a matching-signature forwarder: each write supplies the transaction, owner gate and lock ordering, validates the complete operation, and maps commit failures. Reads supply snapshot semantics and privacy. This is the capability callers should learn instead of learning rank allocation or FK behavior.

### Controller and transport — `api.controller`, `api.dto`

```java
// Wire-only DTOs. They are not the domain service interface.
public record CollectionNameRequest(String name, String description) {}
public record AddCollectionTranscriptRequest(UUID userTranscriptId) {}
public record CollectionPickerRequest(List<UUID> userTranscriptIds) {}
public record MembershipChangeRequest(List<UUID> userTranscriptIds,
                                      List<UUID> addCollectionIds, List<UUID> removeCollectionIds) {}
public record MoveCollectionRequest(Long expectedRevision, List<UUID> movedUserTranscriptIds,
                                    UUID beforeUserTranscriptId) {}
public record CollectionResponse(UUID id, String name, String description, int transcriptCount,
                                 Instant createdAt, Instant updatedAt, long revision, Boolean contains) {}
public record CollectionPickerResponse(CollectionResponse collection, String membershipState,
                                       int matchedTranscriptCount) {}
public record CollectionTranscriptResponse(UUID userTranscriptId, String title, String account,
                                           String platform, String categoryDisplayName, double duration) {}
public record CollectionPageResponse(CollectionResponse collection,
                                     List<CollectionTranscriptResponse> items, String nextCursor,
                                     UUID firstUnloadedUserTranscriptId) {}
public record MembershipReceiptResponse(List<CollectionResponse> collections) {}
public record CollectionErrorResponse(Instant timestamp, int status, String error, String code,
                                      String message, String path) {}

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {
    private final CollectionService service;

    @GetMapping
    public ResponseEntity<List<CollectionResponse>> list(Authentication auth, UUID transcriptId) {
        throw new UnsupportedOperationException("not implemented");
    }
    @PostMapping
    public ResponseEntity<CollectionResponse> create(Authentication auth, CollectionNameRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }
    @PatchMapping("/{id}")
    public ResponseEntity<CollectionResponse> rename(Authentication auth, UUID id,
                                                    CollectionNameRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, UUID id, long expectedRevision,
                                      boolean confirmNonEmpty) {
        throw new UnsupportedOperationException("not implemented");
    }
    @GetMapping("/{id}/transcripts")
    public ResponseEntity<CollectionPageResponse> page(Authentication auth, UUID id,
                                                      String cursor, int size) {
        throw new UnsupportedOperationException("not implemented");
    }
    @PostMapping("/picker")
    public ResponseEntity<List<CollectionPickerResponse>> picker(Authentication auth,
                                                                CollectionPickerRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }
    @PatchMapping("/memberships")
    public ResponseEntity<MembershipReceiptResponse> memberships(Authentication auth,
                                                                MembershipChangeRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }
    @PostMapping("/{id}/transcripts")
    public ResponseEntity<MembershipReceiptResponse> add(Authentication auth, UUID id,
                                                        AddCollectionTranscriptRequest request) {
        // TODO Adapt singleton into the SAME atomic membership operation.
        throw new UnsupportedOperationException("not implemented");
    }
    @DeleteMapping("/{id}/transcripts/{transcriptId}")
    public ResponseEntity<Void> remove(Authentication auth, UUID id, UUID transcriptId) {
        throw new UnsupportedOperationException("not implemented");
    }
    @PatchMapping("/{id}/order")
    public ResponseEntity<CollectionPageResponse> move(Authentication auth, UUID id,
                                                      MoveCollectionRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }
}
```

Actual controller parameters receive the normal `@AuthenticationPrincipal`/Authentication adaptation, `@PathVariable`, `@RequestParam`, `@RequestBody`, and validation annotations; request DTO bindings are not domain authorization. The membership receipt contains summaries only, not an ORM entity or every membership pair. A singleton POST returns that receipt and older `dataRequest` callers can ignore its successful body.

### Error mapping — `exception`

```java
public enum CollectionErrorCode {
    COLLECTION_NAME_BLANK, COLLECTION_NAME_TOO_LONG, COLLECTION_NAME_DUPLICATE,
    COLLECTION_LIMIT, COLLECTION_TRANSCRIPT_LIMIT, COLLECTION_NOT_FOUND,
    TRANSCRIPT_NOT_FOUND, COLLECTION_MEMBER_NOT_FOUND, COLLECTION_CHANGED,
    COLLECTION_DELETE_CONFIRMATION_REQUIRED, COLLECTION_WRITE_CONFLICT,
    COLLECTION_SAVE_FAILED, COLLECTION_UNAVAILABLE, INVALID_REORDER,
    INVALID_CURSOR, INVALID_REQUEST, UNSUPPORTED_FIELD, COLLECTION_INTERNAL_ERROR
}
public final class CollectionException extends RuntimeException {
    private final CollectionErrorCode code;
    public CollectionException(CollectionErrorCode code) {
        throw new UnsupportedOperationException("not implemented");
    }
}
public final class CollectionSqlErrors {
    public static CollectionException translate(Throwable failure) {
        // TODO Driver SQLSTATE + structured constraint name; never message matching.
        throw new UnsupportedOperationException("not implemented");
    }
}
// Only this typed exception is handled globally, including deletion controllers.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CollectionFailureHandler {
    @ExceptionHandler(CollectionException.class)
    public ResponseEntity<CollectionErrorResponse> collection(CollectionException error,
                                                               HttpServletRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }
}
@RestControllerAdvice(assignableTypes = CollectionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CollectionRequestErrorHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class,
                       MethodArgumentTypeMismatchException.class,
                       MethodArgumentNotValidException.class})
    public ResponseEntity<CollectionErrorResponse> malformed(Exception error,
                                                              HttpServletRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CollectionErrorResponse> unexpected(Exception error,
                                                               HttpServletRequest request) {
        // TODO Safe COLLECTION_INTERNAL_ERROR; no exception/body/bind-value logging.
        throw new UnsupportedOperationException("not implemented");
    }
}
```

The typed CollectionFailureHandler applies to Collections and both deletion controllers so deletion conflicts cannot fall into their existing generic 500 path. Order it ahead of the scoped generic handler; explicit typed failures always select the typed advice. Keep malformed-request and unexpected-error handling controller-scoped. Keep truly unexpected internal defects as a sanitized 500 `COLLECTION_INTERNAL_ERROR`; the expected duplicate/limit/ownership/stale/lock cases below are never 500. Do not return or log the underlying SQL exception. Existing ErrorResponse lacks a code (`B/src/main/java/com/app/categorise/api/dto/ErrorResponse.java:10–23`) and the global fallback logs the raw exception and returns its message (`B/src/main/java/com/app/categorise/exception/GlobalExceptionHandler.java:119–133`). The new scoped boundary must intercept all Collections persistence failures before that fallback, with safe operation/code/correlation metadata only.

## Endpoints and errors

All routes authenticate the current person; no client-supplied owner ID and no admin bypass. Error bodies preserve timestamp/status/error/message/path and add a stable `code`. Messages below are owned by the iOS code-to-message mapping; server messages are safe defaults. Responses containing private Collections data use `Cache-Control: no-store` and are never stored in a shared transcript cache.

| Method/path under `/api/v1` | Request / success | Expected failures |
|---|---|---|
| `GET /collections` | 200 array, server A–Z, counts + revisions | 401; 503 `COLLECTION_UNAVAILABLE` |
| `GET /collections?transcriptId={ut}` | 200 same array with contains; validate owned saved transcript | 404 `TRANSCRIPT_NOT_FOUND`; 503 |
| `POST /collections` | `{name}` → 201 summary + Location | 400 blank/too-long/invalid; 409 duplicate; 422 collection limit |
| `PATCH /collections/{id}` | `{name}` → 200 canonical summary | 404 collection; 400 blank/too-long/invalid; 409 duplicate |
| `DELETE /collections/{id}?expectedRevision={r}&confirmNonEmpty={bool}` | 204; collection + memberships removed only | 404 collection; 409 changed/confirmation required; 400 malformed |
| `GET /collections/{id}/transcripts?size=50&cursor=…` | 200 CollectionPageResponse, default 50/max 100 | 404 collection; 409 changed; 400 invalid cursor; 503 |
| `POST /collections/picker` | `{userTranscriptIds:[…]}` → 200 rows none/some/all | 400 invalid selection; 404 transcript; 503 |
| `PATCH /collections/memberships` | `{userTranscriptIds:[…],addCollectionIds:[…],removeCollectionIds:[…]}` → 200 summary receipt | 400 invalid/disjoint sets; 404 collection/transcript; 422 transcript limit |
| `POST /collections/{id}/transcripts` | `{userTranscriptId}` → 200 receipt; existing membership no-op | 404 collection/transcript; 422 transcript limit |
| `DELETE /collections/{id}/transcripts/{ut}` | 204; absent membership no-op after resource ownership check | 404 collection/transcript |
| `PATCH /collections/{id}/order` | move body above → 200 canonical first page | 400 invalid reorder; 404 collection/member; 409 changed |
| Existing `DELETE /transcript` | unchanged single/bulk ID body; <=100; 204 | Existing owned-set validation; typed conflict/save failure when Collections locks fail |
| Existing account delete | existing success contract; all owned collection data gone | Typed conflict/save failure for retryable infrastructure failure, never a new child-FK failure |

All writes additionally return 409 `COLLECTION_WRITE_CONFLICT` on transient concurrency failure or 503 `COLLECTION_SAVE_FAILED` on unavailable persistence. All reads have a typed unavailable response. Unknown fields are invalid for new bodies; for name create/rename only, missing/null `description` is accepted for the old encoder, but non-null description is 400 `UNSUPPORTED_FIELD`. No description UI/storage is added.

An add action can select at most 5,000 distinct transcripts and up to 200 collections: more than 5,000 selected owned transcripts cannot all be added to even one collection, so show the collection-capacity message before issuing that add. This is derived from collection capacity, not an invented picker-selection cap. If a client nevertheless submits a larger add, validate ownership and return 422 `COLLECTION_TRANSCRIPT_LIMIT` without changing any target. Do not silently split an atomic Save into requests. Pure removal/picker reads have no new 100- or 5,000-transcript selection limit; use bounded SQL parameter batching inside the same read/write transaction if the input is larger. The existing <=100 bulk-transcript-delete constraint is independent of picker selection.

| Code / HTTP | User message / saved-state action |
|---|---|
| `COLLECTION_NAME_BLANK` / 400 | “Enter a collection name.” Keep canonical list; keep the draft form open. |
| `COLLECTION_NAME_TOO_LONG` / 400 | “Use 100 characters or fewer.” Keep saved name. |
| `COLLECTION_NAME_DUPLICATE` / 409 | “You already have a collection with that name.” Keep saved name/list. |
| `COLLECTION_LIMIT` / 422 | “You can have up to 200 collections.” Keep list; picker remains open. |
| `COLLECTION_TRANSCRIPT_LIMIT` / 422 | “A collection can hold up to 5,000 transcripts.” Restore all picker ticks; no partial Save. |
| `COLLECTION_NOT_FOUND` / 404 | “This collection is no longer available.” Refresh Home; leave gone detail. Same body for someone else's collection. |
| `TRANSCRIPT_NOT_FOUND` / 404 | “A selected transcript is no longer available.” Restore selection and refresh source. Same body for foreign ID. |
| `COLLECTION_MEMBER_NOT_FOUND` / 404 | “A transcript is no longer in this collection.” Restore and reload collection. |
| `COLLECTION_CHANGED` / 409 | “This collection changed. Its saved order has been reloaded.” Restore then replace with canonical first page. |
| `COLLECTION_DELETE_CONFIRMATION_REQUIRED` / 409 | “Delete this collection? Your transcripts will stay.” Refetch count/revision and ask for confirmation. |
| `COLLECTION_WRITE_CONFLICT` / 409 | “Couldn't save this change. Retry.” Restore last acknowledged state, then reconcile. |
| `COLLECTION_SAVE_FAILED` / 503 | “Couldn't save this change. Try again.” Restore/reconcile; network timeout separately says save could not be confirmed. |
| `COLLECTION_UNAVAILABLE` / 503 | “Collections are unavailable. Retry.” Keep read failure distinct from an empty collection. |
| `INVALID_REORDER`, `INVALID_CURSOR`, `INVALID_REQUEST`, `UNSUPPORTED_FIELD` / 400 | “Couldn't complete this action. Reload and retry.” Preserve saved state; no raw protocol/SQL message shown. |

Structured PostgreSQL mapping after rollback: `23505 + collections_owner_name_uq` → duplicate 409; owner slot constraint → collection limit 422; member slot constraint → transcript limit 422; composite member FK `23503` → owned collection/transcript 404 by constraint name. Name check/length (`23514`/`22001`) map to the validated name error using the safe parsed input, never the driver's message. Rank uniqueness/identity-guard failure is a sanitized internal design defect, not a duplicate-name error; normal rank movement must prevent it. SQLSTATE `55P03`, `40P01`, `40001` → typed write conflict. Resolve exception wrappers to their structured server diagnostics. A rollback cannot be followed by a success receipt.

Both first-attempt and post-auth-refresh branches of `HTTPClient` must retain 404/409 response Data. They currently drop it (`I/Scoop/network/HTTPClient.swift:146–147,155–156`) and CollectionService collapses the other errors (`I/Scoop/service/CollectionService.swift:15–20`). Add an optional structured API-failure value while preserving the existing status categories for other service callers; decode it only in CollectionService into a `CollectionFailure(code,message)` domain error. Unknown/missing code gets a safe fallback. Update exhaustive enum switches as necessary; do not refactor unrelated service clients.

## Deletion: R3, R10 and R11

**R3:** the collection delete holds the owner update gate and collection row, checks the supplied revision, then checks its current count. If nonempty and confirmation is absent, return 409 without deleting anything. If it changed after the user's confirmation, return changed and request fresh confirmation rather than deleting unexpected additions. Delete the collection row; its memberships cascade, and no `user_transcripts` row is deleted.

**R10:** use the existing owned-set validation and <=100 controller contract. Before deleting, take the shared account gate, lock the entire owned transcript set for update in sorted order, then prelock its distinct affected collections in sorted order. `user_transcripts` deletion cascades through the composite membership FK. Delete triggers lower every surviving collection count and invalidate its paging revision in the same commit. Collection ranks may have gaps afterward; paging orders sparse ranks and appends use end rank, so no renumbering is needed. An injected failure rolls back transcripts, memberships, counts and revisions together. The current bulk delete uses `deleteAll(existingEntities)` inside `@Transactional` (`B/src/main/java/com/app/categorise/domain/service/TranscriptService.java:233–268`); **inferred:** failures deferred to commit can escape its local catch. The outer typed mapping must cover that boundary, and its broad catch must not wrap known collection concurrency failures as TranscriptDeletionException.

**R11:** add an exclusive account gate at the start of `UserService.deleteAccount`; delete the user's collections with a set-based owned DELETE before the existing user-transcript cleanup. Their children cascade. Then preserve the existing cleanup sequence and delete the user. The users→collections cascade remains a database safety net for other user-deletion paths. Empty/gone collection parent projection updates do nothing; they must not abort account deletion. Verify the saved-transcript and user rows are gone and shared `base_transcripts` remain. Current account deletion removes saved transcripts first and then other user-scoped rows (`B/src/main/java/com/app/categorise/domain/service/UserService.java:113–131`); its documented atomicity and shared-base preservation are at `:103–108`. Extend its existing test expectations only for this required Collections integration.

## NFR screen

“Meets” below is design intent, not a claim of measured runtime success.

| NFR | Meets | How | Risk / acceptance evidence |
|---|---|---|---|
| N1 | Yes, by design | Synchronous API on the existing single Spring server and Postgres; no additional service, queue, ordering worker, or cache availability dependency. | Pool contention and database failure still affect availability. Exercise alongside the job poller; ordinary service health is insufficient proof. |
| N2 | Conditional on spike | Home reads <=200 stored-count rows; page reads <=101 small summaries through rank index; add/remove are set-based and per-collection locked. Set reorder p90 target <500 ms, including forced 5,000-member rebalance. | No latency measured. Hikari's shared capacity, lock waiting, projection triggers, and multi-target batch volume can break the target. Measure both single and maximum valid picker workloads; do not redefine the N2 promise as a single-item-only test. |
| N3 | Yes, structurally; lifecycle proof pending | One transaction per write, after-rollback error translation, ordered row locks, generated unique name key, finite capacity slots, revision compare-and-move. Expected races have 404/409/422 codes. | Commit-time mapping and add/delete/account lock protocol must be integration-tested through HTTP; Hibernate-only or mocked tests cannot prove it. |
| N4 | Yes, structurally | Slot checks + unique keys enforce 200/5,000 regardless of check-then-act races; normalized names <=100 code points; page default50/max100; normal move153 bytes; full list about195 KB. | Locale semantics and normalization parity need verification; long-lived contention may produce typed retry errors but must never exceed caps. |
| N5 | Not applicable | No backup/recovery promise is introduced; grounding states no DB backup exists. | Migration does not create a backup. Operational rollback of schema is not a recovery guarantee. |
| N6 | Yes, by boundaries; UI/privacy proof pending | iOS18+ existing target; semantic fonts, wrapping names, VoiceOver state/move actions; owner-scoped tables/endpoints; composite FKs; summary/detail membership data kept separate. | Audit actual logs, body/error tracing, SQL bind logging, notification/AI/embedding calls, and iOS shared caches; typed declarations alone do not prove non-disclosure. |

For N6, Collection entities/DTOs have no navigation relationship from BaseTranscript and are never inputs to VideoService, embedding/search builders, AI clients, or NotificationService. Use collection-specific UI/session state, clear it on logout/owner change, and do not persist it in TranscriptCacheStore. Do not add feed-card membership badges or content-enrichment fields. Application admins have no endpoint that selects a different owner; database operators are outside application authorization. Do not log collection names, member UUIDs, payloads, cursor tokens, or caught SQL causes. Allow only operation, duration, safe error code, and request correlation ID. Use bound SQL parameters throughout; never interpolate private values into SQL. Suppress provider error DETAIL logging (including Hibernate SqlExceptionHelper), SQL bind logs and HTTP body/error tracing for these paths. Configure PostgreSQL logging to omit statements/parameters/error DETAIL that could contain names or membership values: verify `log_statement=none`, `log_parameter_max_length=0`, `log_parameter_max_length_on_error=0`, `log_error_verbosity=terse`, and an error-statement threshold that excludes ordinary failures. Audit any external DB/APM logging separately. Existing source enables Hibernate SQL display (`B/src/main/resources/application.properties:26`), so logging verification is a required ops task, not an assumed guarantee.

Offline predicate for these screens/pickers is `session is offline OR connectivity is disconnected`; `ConnectivityMonitor` already exposes path connectivity (`I/Scoop/auth/ConnectivityMonitor.swift:5–14`). Reuse the OfflineUnavailableView pattern (`I/Scoop/views/Components/BottomNavBar.swift:36–50`) and the message “Collections unavailable offline.” Picker buttons are disabled and a sheet must recheck before presentation. If connectivity drops while open, close/disable the picker and show unavailable; retain the last acknowledged state only for later reconciliation, not editable offline collections. A path monitor is advisory: a transport failure with no working connection takes the same unavailable route even before token refresh changes session mode. No offline write queue.

## Requirement coverage

| Requirement | Design behavior and primary proof |
|---|---|
| R1 | Canonical validated name, owner-unique key, finite owner slots; Home and picker create; name/cap race HTTP tests. |
| R2 | Same name type/constraint, owner catalog gate, canonical response; rename collision test including concurrent rename/create. |
| R3 | Locked current revision/count + explicit nonempty confirmation; cascade membership only; transcript survival test. |
| R4 | Server A–Z key with UUID tie, stored counts, Home link, true empty state distinct from failure; list/picker UI tests. |
| R5 | Rank order with consistent versioned pages default50; summary tap routes owned saved UUID to detail; across-page order proof. |
| R6 | Same picker for selected feed/category/search IDs and detail singleton; one atomic delta; append only missing pairs; full-cap re-add succeeds; cap error shown. |
| R7 | Screen singleton remove or picker remove delta; idempotent absent pair; rollback and count proof. |
| R8 | Singleton contains / multi-selection states; detail renders included collections via a private separate request; foreign saved UUID404. |
| R9 | Move-before-anchor block contract; unloaded lookahead anchor; bounded rank rebalance; restore/reload on all failures; concurrency and prefix-boundary tests. |
| R10 | Existing single/bulk<=100 transcript delete, ordered locks + composite FK cascades + projection triggers; every affected count/revision updates atomically. |
| R11 | Owner-exclusive account gate, collection cleanup before existing cleanup, users cascade safety net; actual account-delete integration test. |
| R12 | Machine codes preserved through HTTP/auth retry; per-code messages and canonical rollback/reconciliation in every screen. |
| R13 | OfflineUnavailableView; picker presentation recheck/disabled action; no editable cached collection data or deferred saves. |

## How tests will prove N3 and N4

Apply the `test-audit` authoring gate to implementation tests: protect observable HTTP/storage/UI contracts, credible races, and lifecycle regressions; no tests that merely repeat this document's fields or mock the lock implementation. Current `application-test.properties` uses Hibernate create and disables Flyway (`B/src/test/resources/application-test.properties:10,15`), and the repository test uses `postgres:15-alpine` (`B/src/test/java/com/app/categorise/data/repository/UserTranscriptRepositoryTest.java:27–43`). Those tests cannot validate these migrations/triggers/cascades.

Create a focused Collections integration fixture with `@SpringBootTest(webEnvironment=RANDOM_PORT)` and Testcontainers, overriding **in the test class**:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.validate-on-migrate=true
app.jobs.poller.enabled=false
app.openai.mode=mock
```

Use a PG15 image that includes pgvector, such as a pinned `pgvector/pgvector:pg15` digest, rather than vanilla postgres:15-alpine: migration V25 creates `vector` and an HNSW index (`B/src/main/resources/db/migration/V25__add_embedding_to_base_transcripts.sql:1–6`). Run **all actual migrations V1…V28 on an empty database**; do not create just the new tables through Hibernate or copy SQL into tests. Catalog-check that generated name key, slot checks, deferrable rank constraint, composite cascades, and all projection triggers are present. Leave the general test profile unchanged. Start application security and use real authenticated identities for ownership/404 tests; if the profile disables security, explicitly override it for this fixture.

Tests must commit real transactions. Do not annotate race tests with a shared test transaction. Seed through one committed connection, release two independent authenticated HTTP calls together with a barrier, and inspect final state through a fresh connection after both responses complete. Use disjoint requests to prove final capacity, not two requests adding the same member. To target a particular overlap without production test seams, use a third SQL connection to hold the documented row gate while both requests become blocked, then release it. Tests assert public status/code/state, not internal method invocation counts.

| Primary test owner | Observable assertion / credible failure caught |
|---|---|
| Migration + HTTP name race | At199 or fewer, two creates ` Recipes ` / `recipes`: one201, one409 with duplicate code, one canonical collection. Concurrent rename/create and rename/rename onto one normalized key: one winner, one409; no500. Catches case-sensitive keys and unsafe commit mapping. |
| Migration + HTTP collection cap | Start199, two different names: one201, one422 COLLECTION_LIMIT, final200; start200, add no row. Direct SQL tries slot201/check or reusedslot/unique; DB refuses. Catches service-only caps. |
| Migration + HTTP member cap | Start4,999, two different owned saved UUIDs: one200, one422 limit, final5,000; at5,000 re-add existing succeeds with no count/revision/order change. Direct SQL slot5,001 or reusedslot rejected. |
| Migration + HTTP atomic delta | Save to several collections with one full/foreign/gone: failure code and all preexisting pairs/counts/revisions unchanged. Successful multi-target save then repeat has one append per new pair and unchanged second revision. Catches per-transcript transactions and false successful subsets. |
| Migration + HTTP reorder/page | Seed5,000, fetch50, move item10 before unseen51: unread suffix remains in order, moved block correct. Walk every page at one revision: exactly5,000 distinct IDs in saved order. A concurrent move makes oldcursor409; two equal-revision moves produce one200 and one409. |
| Real delete lifecycle | Delete one and then100 owned transcripts distributed across several collections; all memberships gone, exact counts, revision greater, oldcursors409, base transcripts survive. Inject a DB failure with a test-installed failing trigger and assert rollback of all four data sets. Remove the test trigger before the next case. |
| Real account lifecycle | Account-delete endpoint succeeds with populated collections; user/collections/memberships gone; other person's rows and shared bases survive. Add vs delete and account delete vs name/add races end in permitted responses, no deadlocks exposed as500, no orphans. |
| Boundary validation + ownership | 0/100/101 code-point names, whitespace-only and canonical Unicode names; default50, max100; malformed body/cursor/reorder; foreign IDs behave like missing. Pure value validation gets its own narrow tests; ownership is proved through HTTP. |
| iOS service/UI | Exact page/move encoding; 404/409/422 bodies retained on first/auth-retry paths; 50-item-prefix end uses unreadanchor; load-more guard; failure restores saved order/ticks/name; offline cannot open picker; accessible labels and text at accessibility sizes. |

During all HTTP race scenarios assert no response is 500 and inspect the response code, not status alone. After commit assert `transcript_count = COUNT(memberships)` and that every membership's owner matches both parents. Check every rank distinct and slot in range. These assertions are storage contracts and survive implementation refactors. A barrier stress repetition is supplemental; the finite-slot/unique constraints and commit-boundary tests provide the deterministic N3/N4 proof. For mutation-failure rollback, the failing trigger lives only in the disposable test database; no production flag or failure-injection API is introduced.

## Cheapest Postgres 15 spikes

These are proposed experiments, **not run**. Use a disposable PG15+pgvector container migrated from the actual main migrations plus the proposed V28, recording PostgreSQL version, extension version, locale, image digest, machine and application configuration. No production data or repository edits are needed. The riskiest assumption is that cascade projection triggers and the lock order stay correct under the existing JPA deletion lifecycle; prove that first.

| Spike | Exact measurement | Pass bar / what breaks the design |
|---|---|---|
| 1. Cascades and deletion locks | Seed several populated collections and saved transcripts. Run single delete,100-ID JPA delete, collection delete, actual account delete, then paired add/delete and add/account-delete races. Capture HTTP status/code, committed pair counts, collection count/revision, remaining base IDs, `pg_locks` wait relationships and deadlock errors. Repeat controlled concurrent cases100 times. | Every count equals surviving membership count; every cascade changes surviving collection revision; account delete succeeds absent injected infra failure; no orphan or other-owner mutation; no unexpected500. Any reproducible trigger/cascade failure or cycle without a typed rollback rejects this lifecycle shape. |
| 2. Rank exhaustion and latency | Seed5,000 rows. Repeatedly move distinct rows into the same narrow gap until scale18 allocation must rebalance. Count changed membership rows from UPDATE RETURNING; count collection-row updates with a test-only audit trigger. Record endpoint durations including response serialization, database statement/lock times, and WAL bytes separately. Run1,000 ordinary moves and100 forced rebalance moves. | Exact intended order with unique ranks; ordinary single move <=1 membership +1 projection row; rebalance <=5,000 memberships +1 projection row; no intermediate generation visible; p90 ordinary and rebalance each<500 ms. Failure of precision correctness rejects ranks; latency failure reopens storage/response policy. |
| 3. N2 access paths and maximum batch | With Hikari10 and job poller enabled, seed200 collections of5,000 memberships and realistic source summaries. Measure1,000 completed requests each for list, first/deep page, single add/remove on capacity fixtures, and maximal valid picker/save workloads, at1 and8 concurrent client requests. Record p90 arrival-to-last-response-byte, payload bytes, pool waits, errors, and EXPLAIN(ANALYZE,BUFFERS) plans. | Each required operation's p90<500 ms in the declared workload, no N3/N4 violations, page query bounded to101 fetched summaries, list does not aggregate the million membership rows. Maximum batch failures must be surfaced; do not pass by silently limiting selection or reporting only singleton performance. |
| 4. Caps, name policy, commit mapping | Hold an owner/collection gate from a SQL connection, launch paired real HTTP writes, release the gate, and run the199/4,999 boundary and case-collision cases. Include composed/decomposed names, Unicode spaces,100/101 code points and chosen locale case pairs. Measure final cardinality, normalized saved values, status/code and committed/rolled-back state. | Final caps exactly held, named failures404/409/422 as specified, no500/SQL text, normalization parity, A–Z list order from one database key. If PostgreSQL lower does not satisfy required case policy, revise the canonical key before accepting the migration. |

The maximal picker spike is deliberate: a successful Save can touch many collections and membership pairs. **Guess:** that workload may fail the strict N2 interpretation even though singleton operations pass. If it fails, synthesis must either choose a different shape or obtain an explicit product decision about the batch latency contract; the runner does not silently relax N2 or introduce an asynchronous partial save.

## Tradeoffs accepted

- We accept private capacity-slot allocation in exchange for a hard database proof of the caps without a racy COUNT check or a cross-request reservation table.
- We accept database-maintained count/revision projections in exchange for a bounded Home read and automatic correctness on transcript cascades. Their cascade semantics require real migration tests.
- We accept rank rebalancing at finite precision in exchange for small ordinary drags without dense range rewrites. The worst case is explicitly bounded at5,000.
- We accept invalidating old page generations in exchange for consistent browsing without persisting historical membership snapshots.
- We accept an owner-local exclusive gate for catalog/account mutations and per-collection serialization in exchange for predictable name/cap/deletion outcomes; ordinary different-collection writes still coexist.
- We accept updating the dormant iOS contract and collection row UI in exchange for unambiguous paged reordering, typed errors and atomic picker Save.

## Alternatives considered

**Dense ordinal join rows + move command.** Store one bounded integer position per member and shift the affected interval on every move, with a deferrable unique position constraint and normalization after deletions. This hides order complexity behind the same good move API and is simpler to reason about numerically. It loses here because routine long-distance drags write thousands of rows, and using the same field for capacity and display order couples append/deletion gap handling. Sparse rank plus an independent bounded slot keeps ordinary moves local while retaining database caps. If the rank spike fails, dense ordinal storage is the first whole-storage alternative to reconsider.

**Immutable ordered UUID snapshot per revision.** Write a new full ordered vector and page it by revision, with relational memberships for ownership/deletion. This hides paging invalidation by retaining prior snapshots and makes old cursors stable. It loses because every move rewrites the full5,000-ID vector, deletions must synchronize a second ordering representation and its retained snapshots, and callers/ops need snapshot expiry semantics. Its public surface can be small, but it hides substantially more lifecycle policy than this feature requires.

**Preserve full replacement-order API.** Require fetching all IDs first and PUT/PATCH one complete permutation. It hides no loaded-prefix complexity from the caller: the caller must discover/fetch every member and handle stale order during that fetch. It loses the required partially loaded drag experience and carries approximately200 KB for every reorder; accepting partial replacement without a defined anchor would silently corrupt saved order.

## Design red-flag screen

| Flag | Result / revision |
|---|---|
| Shallow module | Pass. Callers request list/page/picker/create/rename/delete/change/move outcomes; they do not allocate slots, manage transactions, rebalance ranks, or sequence per-transcript saves. The iOS Save is one call. |
| Information leakage | Pass with a stated risk. Wire DTOs terminate at controllers/service adapters; rank/slot stay private, cursor opaque. Count derivation and revision advancement live only in SQL projections. The shared deletion lock protocol is an unavoidable cross-lifecycle contract and must stay in the named repository integration, not be copied ad hoc. |
| Temporal decomposition | Pass. There is no load/validate/transform/save module chain. Collection policy stays in one service and storage knowledge in one repository. Lock helpers are private lifecycle tools, not a public choreography API. |
| Pass-through methods | Revised. Service owns transaction/authorization/locking/translation, not simple forwarding. Singleton controller routes adapt wire operations into the one membership domain command and preserve useful compatibility. No extra mapper/service framework is introduced. |

## Synthesis decision

Not populated by this runner. Candidate2 proposes sparse numeric ranks, finite capacity slots, version-invalidating cursor pages, and an atomic membership-delta API. Arena must compare whole shapes and select/adapt/reject this candidate; no claim is made about other runners or their files.

## Open questions and risks

- Will the actual database locale's case behavior meet the agreed meaning of “ignoring case” for non-English names? Default here is NFC plus PostgreSQL lower; Spike4 resolves whether it must change.
- Will the strict p90 target hold for a maximum valid multi-selection Save and forced rank rebalance with the job poller sharing the pool? Default is to test those workloads, not silently exempt them.
- Do PG15 cascade transition tables and collection-parent deletion behave correctly with Hibernate's actual flush sequence? Spike1 is the acceptance gate for that assumption.
- Does the “restore then reload first page” interaction give sufficient orientation during a deep-list move? Default here returns a canonical first page and announces the update; iOS UX proof must verify it rather than retaining invalid cursors.

## What this design obliges us to build

| Area | Concrete work items |
|---|---|
| Backend | Collection domain values; CollectionService transaction/authorization boundary; native-SQL CollectionRepository with capacity allocation, rank planning and snapshot reads; controllers/wire DTOs; authenticated cursor codec; structured SQL/code mapping; safe scoped error advice; singleton adapters and atomic delta/picker endpoints. |
| Backend lifecycle | Add the account gate and sorted transcript/collection prelocks to actual transcript deletion; preserve typed errors past its broad catch; delete owned collections before account cleanup; cover transaction-completion mapping for both deletion paths. |
| iOS | Configure CollectionService; restore Home entry/list/empty state; remove description UI; introduce summary row + UUID detail navigation; replace offset paging/reorder; add one-fetch/page-generation guard; derive unloaded anchor; support accessible far-destination selection; canonical success and failure reconciliation; feed/category/search/detail picker with mixed states, create and explicit Save; typed error decoding retaining404/409 bodies in both HTTP branches. |
| iOS availability/accessibility | Connect Collections screens and picker presentation to offline session/connectivity; use OfflineUnavailableView; semantic fonts/wrapping; VoiceOver count/tick/mixed/remove/move labels and actions; clear private transient state on owner change/logout. |
| Migration | Author V28 with composite ownership FK key, collection/membership constraints/indexes, normalization function and projection/identity triggers; validate through all migrations on emptyPG15+pgvector; verify deployed encoding/locale and pin test image digest. |
| Tests | Add actual Flyway-backed HTTP concurrency/cap/deletion/page fixture; migration catalog and rollback proofs; rank and name-policy spikes; narrow domain validation tests; service contract/auth-retry tests; picker/reorder/offline UI proofs; accessibility and private-data boundary verification. |
| Ops | Run the declared latency/load spikes with pool/job-poller conditions; record safe endpoint latency/code metrics; configure private bind/body/error logging protection and no-store headers; supply/rotate cursor secret with reload-on-invalid-token behavior; deploy backend before enabling updated Home UI; smoke create/list/page/move/delete/account cleanup using disposable accounts. |

## Next implementation step

Build the disposable PG15 Flyway lifecycle fixture and run the cascade/deletion-lock spike before filling in the service or iOS sketch; revise the storage shape if it fails.
