# Handoff: Semantic Search — Frontend Implementation

## Goal

Add a semantic search experience to the app so users can type natural language (e.g. "pasta recipe", "skincare routine for dry skin") and see the transcripts that best match in meaning — not just keyword matches. The backend is fully implemented. This document covers everything the frontend needs to wire it up.

---

## How the backend works (brief)

When a video is transcribed, the backend sends its text to OpenAI's embedding API and stores a 1536-dimension vector in Postgres. When a user searches, the backend embeds their query the same way, then runs a cosine similarity query in Postgres to find the closest matching transcripts. No summarisation or generation — pure retrieval ranked by semantic similarity.

---

## New API endpoint

```
GET /api/v1/transcripts/search
Authorization: Bearer <user token>
```

### Query parameters

| Param        | Type    | Required | Default | Description                                                     |
|--------------|---------|----------|---------|-----------------------------------------------------------------|
| `q`          | string  | yes      | —       | The user's search query                                         |
| `limit`      | integer | no       | 10      | Max results to return (capped at 50)                            |
| `categoryId` | UUID    | no       | —       | Scope results to a single category. Omit to search all categories. |

### Example requests

```
# Search across all categories
GET /api/v1/transcripts/search?q=skincare&limit=10
Authorization: Bearer eyJhbGc...

# Search within a specific category
GET /api/v1/transcripts/search?q=skincare&categoryId=3f5a1b2c-...&limit=10
Authorization: Bearer eyJhbGc...
```

### Response

Returns the **same shape** as the existing transcript list endpoint (`GET /api/v1/transcripts`). It's a `List<TranscriptDtoWithAliases>`, ordered from most to least semantically similar.

```json
[
  {
    "id": "uuid",
    "videoUrl": "https://...",
    "transcript": "...",
    "structuredContent": "...",
    "description": "...",
    "title": "...",
    "generatedTitle": "...",
    "duration": 120.0,
    "uploadedAt": "2025-01-01T00:00:00Z",
    "accountId": "...",
    "account": "...",
    "identifierId": "...",
    "identifier": "...",
    "alias": "...",
    "categoryId": "uuid",
    "category": "Cooking",
    "createdAt": "2025-01-01T00:00:00Z",
    "notes": null,
    "platform": "YOUTUBE",
    "subcategoryId": null,
    "subcategory": null
  }
]
```

### Error cases

- `400` — `q` param is missing
- `401` — missing or invalid token
- Empty array `[]` — no transcripts matched (or no transcripts have been indexed yet)

---

## Important caveat: existing transcripts

Only transcripts saved **after** this backend was deployed will have embeddings and appear in search results. Transcripts created before this change have a `NULL` embedding and are excluded. There is no backfill implemented yet — something to keep in mind when testing with older data.

---

## Backend files changed (for context only — no frontend changes needed here)

### Created
| File | Purpose |
|------|---------|
| `src/main/resources/db/migration/V25__add_embedding_to_base_transcripts.sql` | Enables pgvector extension, adds `embedding vector(1536)` column + HNSW index to `base_transcripts` |
| `src/main/java/.../data/client/openai/EmbeddingClient.java` | Interface: `float[] embed(String text)` |
| `src/main/java/.../data/client/openai/EmbeddingClientImpl.java` | Prod impl — calls OpenAI `text-embedding-3-small` |
| `src/main/java/.../data/client/openai/MockEmbeddingClient.java` | Dev impl — returns deterministic fake embeddings, no API key needed |

### Modified
| File | Change |
|------|--------|
| `docker-compose.yml` | Postgres image changed from `postgres:15-alpine` → `pgvector/pgvector:pg15` |
| `src/main/java/.../data/repository/CustomUserTranscriptRepository.java` | Added `searchByEmbedding(userId, queryEmbedding, limit, categoryId)` method to interface |
| `src/main/java/.../data/repository/UserTranscriptRepositoryImpl.java` | Implemented `searchByEmbedding` — native SQL cosine similarity query scoped to user; optional `category_id` filter; cosine distance threshold of `0.8` to filter irrelevant results |
| `src/main/java/.../domain/service/VideoService.java` | Injected `EmbeddingClient` + `JdbcTemplate`; calls `generateAndStoreEmbeddingIfAbsent` after a new transcript is saved |
| `src/main/java/.../domain/service/TranscriptService.java` | Added `semanticSearch(userId, query, limit, categoryId)` method |
| `src/main/java/.../api/controller/TranscriptController.java` | Added `GET /api/v1/transcripts/search` endpoint with optional `categoryId` param |

---

## What the frontend needs to do

### 1. Search input UI
Add a search bar to the transcripts screen. This could be:
- A persistent search bar at the top of the transcript list
- Or a search icon that expands into an input

When the user types and either hits enter or after a debounce (e.g. 500ms), trigger the search.

### 2. Call the search endpoint
When a query is present, call `GET /api/v1/transcripts/search?q=<query>` instead of the regular `GET /api/v1/transcripts`.

When the query is cleared/empty, revert to the normal transcript list.

### 3. Display results
The response shape is identical to the normal transcript list — reuse the existing transcript card/cell component. No new display component needed.

You may want to show a "No results found for X" empty state when the array is empty.

### 4. Loading & error states
- Show a loading indicator while the search request is in flight (embedding + DB query typically takes ~300–600ms)
- On error, show a generic retry message

### 5. Category-scoped search
The search endpoint now accepts an optional `categoryId` parameter. If the user is already browsing inside a category, pass that category's ID when calling search — results will be scoped to that category only.

If the user is on the all-transcripts view (no active category), omit `categoryId` and search runs across everything.

Suggested behaviour:
- User is inside a category → search bar searches within that category by default
- Optionally expose a toggle "Search all categories" to override this

---

## Suggested UX flow

```
User opens transcript list
    → normal filtered list loads as today

User types in search bar (debounced 500ms)
    → call GET /search?q=...
    → replace list with ranked results
    → show result count: "5 results for 'pasta recipe'"

User clears search
    → revert to normal GET /transcripts list
```

---

## Notes for the agent

- The endpoint is already authenticated — pass the same Bearer token used for all other API calls
- The `limit` param defaults to 10; you can expose this as a "load more" / pagination mechanism if needed
- The order of results matters — index 0 is the closest semantic match, show them top-to-bottom as-is
- Do not sort or filter the search results on the client side — the ranking comes from the DB
