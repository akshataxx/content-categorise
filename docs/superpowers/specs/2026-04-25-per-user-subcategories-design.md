# Per-User Subcategories — Design Spec

**Date:** 2026-04-25
**Status:** Draft (awaiting user review)
**Scope:** Add a single level of per-user subcategories beneath the existing
shared root categories, with no AI involvement and no admin-managed
subcategory taxonomy.

---

## 1. Goal

Let a user organise their transcripts one level deeper than the shared root
categories (Recipes, Tech, Workouts, …). Each user curates their **own**
subcategories under each root; one user's subcategories are never visible to
another user. A transcript may optionally be assigned to one of the user's
subcategories under the same root the AI assigned to it.

## 2. Non-goals

- More than two levels of hierarchy (parent + subcategory only).
- Subcategories shared across users.
- Admin-managed / system-seeded subcategories.
- Subcategories visible to the AI categorisation step.
- Aliases on subcategories (the subcategory's own `name` is the user's chosen
  label by definition).
- Many-to-many tagging.
- Moving a subcategory between parents (a subcategory's parent is fixed at
  creation).

## 3. Constraints established during brainstorming

These shaped the design and are deliberately recorded so the implementation
does not relitigate them.

1. The AI categorisation step continues to choose only from **root categories**
   and remains entirely unaware of subcategories.
2. Subcategories are **per user**: each user has their own private set under
   each root.
3. A subcategory's parent is **immutable** after creation.
4. Maximum depth is **exactly two** (root + subcategory).
5. A transcript has **at most one** subcategory (single-valued, not a tag set).
6. Subcategories are first-class entities — they exist independently of any
   transcript that happens to be assigned to them, can be renamed, can have a
   description, and can be empty.
7. The existing alias system (`category_aliases`) is unchanged. It applies to
   roots only. Subcategories carry their user-chosen name directly.

## 4. Data model

### 4.1 `category` table — unchanged

Roots remain the only rows in `category`. The `parent_id` column considered
during brainstorming is **not** added; the hierarchy lives across two tables
because the two layers have different ownership (system vs. user).

### 4.2 New table: `user_subcategory`

```sql
CREATE TABLE user_subcategory (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    parent_id   UUID         NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
    name        VARCHAR(255) NOT NULL,
    description TEXT         NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_subcategory_user_parent_name
        UNIQUE (user_id, parent_id, name)
);

CREATE INDEX idx_user_subcategory_user_id   ON user_subcategory(user_id);
CREATE INDEX idx_user_subcategory_parent_id ON user_subcategory(parent_id);
```

Key properties:

- `parent_id` is `NOT NULL` and `ON DELETE RESTRICT`. Deleting a root that any
  user has subcategories under is rejected. (We don't anticipate deleting
  roots; the constraint exists to prevent silent data loss.)
- `(user_id, parent_id, name)` is unique — a user cannot have two
  subcategories with the same name under the same root, but two users can
  independently use the name "Vegan" under "Recipes."
- `parent_id` is fixed at creation; the service layer rejects any update that
  changes it. (See §6.)

### 4.3 New column on `user_transcripts`

```sql
ALTER TABLE user_transcripts
    ADD COLUMN user_subcategory_id UUID NULL
        REFERENCES user_subcategory(id) ON DELETE SET NULL;

CREATE INDEX idx_user_transcripts_user_subcategory_id
    ON user_transcripts(user_subcategory_id);
```

The existing `category_id` column is unchanged in shape and meaning: it points
at the AI-assigned root and is not modified by the subcategory feature.

### 4.4 Cross-FK invariant

When `user_subcategory_id` is set, the application enforces:

```
user_subcategory.parent_id  ==  user_transcripts.category_id
user_subcategory.user_id    ==  user_transcripts.user_id
```

Both checks live in `UserTranscriptService.setSubcategory(...)` and are pinned
by tests. The DB does not enforce them — both checks would require triggers or
composite FKs that don't pull their weight given how rarely the column is
written.

## 5. AI / categorisation flow

No behavioural change. `CategorisationService` continues to send a list of
root category names to OpenAI and receive one of them back. The AI never sees
or writes anything about subcategories.

A small refactor to reduce the chance of accidental regression:

- `CategoryService.getAllCategoryNamesCreatedBySystem()` is renamed to (or
  joined by) `getAllRootCategoryNames()`. Functionally identical today
  (`category` only contains roots), but the name documents the contract that
  the AI must only see roots.

## 6. Service layer

### 6.1 `UserSubcategoryService` (new)

Lives in `domain/service`. Pure CRUD with the invariants the data model
requires.

- `createSubcategory(userId, parentId, name, description)`
  - Validates `parentId` references an existing `category` row.
  - Validates `(userId, parentId, name)` is unique.
  - Returns the created `UserSubcategoryEntity`.
- `renameSubcategory(userId, subcategoryId, newName)`
  - Validates ownership (`subcategory.user_id == userId`).
  - Validates new `(userId, parentId, newName)` is unique.
  - **Does not** allow `parentId` to change.
- `updateDescription(userId, subcategoryId, description)`
  - Validates ownership. `description` may be null.
- `deleteSubcategory(userId, subcategoryId)`
  - Validates ownership.
  - DB cascade (`ON DELETE SET NULL`) clears
    `user_transcripts.user_subcategory_id` for any affected rows.
- `findByUser(userId)`
  - Returns all of the user's subcategories, suitable for grouping by parent.
- `findByUserAndParent(userId, parentId)`
- `findById(subcategoryId)` — for internal validation uses.

### 6.2 `TranscriptService` (extended)

`TranscriptService` is the existing home for per-user transcript operations
(it already owns notes updates and similar per-user mutations); it gains a
single new method that coordinates the cross-FK invariant in §4.4:

- `setSubcategory(userPrincipal, userTranscriptId, subcategoryId)`
  - `subcategoryId == null` clears the assignment.
  - Otherwise, loads the user transcript and the subcategory; rejects if
    either is not owned by the principal, or if the subcategory's `parent_id`
    does not equal the transcript's `category_id`, or if the transcript has no
    `category_id` set at all.
  - Persists the change.

The new method does not touch `VideoService` (which is responsible for the
ingest/AI flow) or `VideoMapper` (which only does pure conversion); the
subcategory assignment is a user-initiated mutation and belongs alongside the
existing notes-update path on `TranscriptService`.

### 6.3 `CategoryAliasService` — unchanged

Aliases continue to be keyed on `(user_id, category_id)` against the global
`category` table. They do not apply to subcategories, by design.

## 7. API surface

All endpoints require an authenticated user. All responses scope to that
user's data.

### 7.1 `GET /api/v1/categories`

Returns the user's view of the taxonomy: every shared root, plus that user's
subcategories under it. New endpoint; today no equivalent exists.

```json
[
  {
    "id": "…",
    "name": "Recipes",
    "description": "Curated list of delicious recipes",
    "subcategories": [
      { "id": "…", "name": "Vegan",     "description": null, "createdAt": "…" },
      { "id": "…", "name": "Quick",     "description": "30 min or less" }
    ]
  },
  {
    "id": "…",
    "name": "Tech",
    "description": "Nerrrrd",
    "subcategories": []
  }
]
```

### 7.2 Subcategory CRUD (under a root)

- `POST   /api/v1/categories/{rootId}/subcategories` — body: `{ name, description? }`
- `GET    /api/v1/categories/{rootId}/subcategories`
- `PATCH  /api/v1/subcategories/{id}` — body: `{ name?, description? }`
  (no `parentId` field; ignored if sent)
- `DELETE /api/v1/subcategories/{id}`

These are exposed immediately because per-user subcategories are inherently
user-created; there is no "system seed" mode for them.

### 7.3 Assigning a subcategory to a transcript

- `PATCH /api/v1/transcripts/{userTranscriptId}/subcategory`
  - Body: `{ "subcategoryId": "…" }` to set, or `{ "subcategoryId": null }` to
    clear. Sits alongside the existing `PATCH /{userTranscriptId}/notes`
    endpoint on `TranscriptController` and follows the same conventions.
  - Returns the updated `TranscriptDtoWithAliases`.
  - Error mapping is delegated to the project's existing
    `GlobalExceptionHandler`. Conceptually:
    - "Transcript or subcategory not found / not owned by the user" →
      not-found semantics.
    - "Subcategory's `parent_id` does not equal the transcript's
      `category_id`, or the transcript has no `category_id` yet" →
      validation/conflict semantics.
  - The exact HTTP status codes follow whatever `GlobalExceptionHandler`
    currently maps the corresponding domain exceptions to (e.g. the existing
    `TranscriptNotFoundException` for the not-found case); a new domain
    exception is added for the parent-mismatch case if no suitable one
    exists.

### 7.4 `TranscriptDtoWithAliases` — extended

Two new nullable fields, populated when `user_subcategory_id` is set:

- `subcategoryId` (UUID, nullable)
- `subcategory`   (String name, nullable)

Existing fields (`categoryId`, `category`, `alias`, etc.) keep their current
meaning. Old clients see no breaking change; new clients render breadcrumbs
as `category > subcategory` when `subcategory` is non-null.

## 8. Mapping

`VideoMapper.buildResponse` gains:

```java
UUID   subcategoryId   = userTranscript.getUserSubcategory() == null
                         ? null : userTranscript.getUserSubcategory().getId();
String subcategoryName = userTranscript.getUserSubcategory() == null
                         ? null : userTranscript.getUserSubcategory().getName();
```

These are passed into the extended `TranscriptDtoWithAliases` constructor.
No other mapping changes are required; the alias lookup keyed on the root's
`categoryId` is unchanged.

A new `UserSubcategoryMapper` (under `application/mapper`) handles the
entity ⇄ DTO conversion for the new endpoints.

## 9. Migration

One Flyway migration:

`V24__add_user_subcategory.sql`

- Creates the `user_subcategory` table, its unique constraint, and its two
  indexes.
- Adds the `user_subcategory_id` column to `user_transcripts` with the FK and
  index.

No backfill is needed; every existing `user_transcripts` row remains valid
with `user_subcategory_id = NULL`.

## 10. Seeding

`CategorySeeder` is unchanged — it seeds the shared roots only. There is no
seeding of `user_subcategory` rows; per-user data is created at runtime by
the user.

## 11. Testing

- **Repository tests** (`UserSubcategoryRepositoryTest`):
  - `(user_id, parent_id, name)` uniqueness is enforced.
  - `findByUser`, `findByUserAndParent` return the correct rows.
  - `ON DELETE CASCADE` from `users`, `ON DELETE RESTRICT` from `category`,
    `ON DELETE SET NULL` from `user_subcategory` to `user_transcripts`.
- **Service tests** (`UserSubcategoryServiceTest`):
  - Create rejects unknown parent.
  - Create rejects duplicate `(user_id, parent_id, name)`.
  - Rename rejects ownership mismatch and duplicate name.
  - Update never changes `parent_id`.
  - Delete only succeeds for owner.
- **Cross-FK invariant tests** (`UserTranscriptServiceTest`):
  - `setSubcategory` rejects when subcategory's `parent_id` ≠ transcript's
    `category_id`.
  - Rejects when subcategory belongs to a different user.
  - Rejects when transcript has no `category_id` yet.
  - Accepts and clears via `null`.
- **Mapper test** (`VideoMapperTest`):
  - `subcategoryId` / `subcategory` populated when present, null otherwise.
- **Controller tests** for `GET /api/v1/categories` (shape includes
  per-user subcategories), CRUD endpoints (auth + 404), and
  `PATCH /transcripts/{id}/subcategory` (happy path + 404 + 409).
- **Regression test** that the AI prompt only contains root names (i.e. the
  list passed into `OpenAIClient.classifyAndSuggestAlias` is exactly
  `getAllRootCategoryNames()`).

## 12. Risks and decisions deferred

- **Cross-FK invariant is enforced in app code, not the DB.** This is a known
  trade-off; we accept it because the column is rarely written and a unit test
  pins the behaviour. If we later see drift, a `BEFORE INSERT/UPDATE` trigger
  on `user_transcripts` is a small follow-up.
- **No alias system for subcategories.** If users later ask for nicknames over
  their own subcategories, that's a no-op (the subcategory's `name` already
  is the alias).
- **Sharing subcategories across users** is explicitly out of scope. If we
  ever want it, the migration would be to introduce a separate
  `shared_subcategory` table; the per-user table stays for personal labels.
- **Pagination on `GET /api/v1/categories`** is not specified. Expected size
  is roots × per-user subcategories — small enough to return in full for now.
  Revisit only if a user accumulates hundreds of subcategories under a single
  root.

## 13. Out-of-scope follow-ups (intentional)

- A future endpoint to let users reorder subcategories (display order
  column).
- A future per-subcategory description / icon / colour beyond the basic
  description column already included.
- A future "promote my subcategory to a shared root" admin flow.
