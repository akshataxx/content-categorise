# Design Spec: Search Architecture Overhaul (RRF Fusion, Tag Filtering, Snippets, Entities)

**Status:** Draft
**Author:** Rovo Dev
**Date:** 2026-06-29
**Repos affected:** Backend (`content-categorise`), iOS (`Scoop`)
**Supersedes:** `2026-06-26-auto-tagging-entity-extraction-design.md` (the deferred
auto-tagging spec is folded in here as the *Filtering* and *Entities* stages of a
coherent search pipeline, rather than a standalone bolt-on).

> **Stacking & migration numbering.** This stack branches **after** the parallel
> 3-PR feature stack (Collections = V28, Analytics optional index = V29,
> Notifications = no migration). The first free Flyway version when this work
> starts is therefore **V30**. All migration numbers below (V30, V31, V32) assume
> V28/V29 have landed; rebase to the next free version if that changes.

---

## 1. Goal

Replace the current hand-tuned hybrid scorer with a principled, staged search
pipeline, and add the deterministic filtering / presentation layers it has been
missing:

1. **Ranking/fusion** — replace the arithmetic score with **Reciprocal Rank
   Fusion (RRF)** over independent ranked lists, and fix the FTS index so the
   text-rank list is index-backed instead of seq-scanned.
2. **Filtering** — promote LLM-derived `tags` to a first-class, indexed
   `text[]` column for **deterministic** `&&` (overlap) filtering.
3. **Presentation** — add `ts_headline` snippets so the UI can answer "why did
   this match?".
4. **Entities** — store extracted entities (`people`/`products`/`places`) as an
   indexed `jsonb` column, displayed in v1 (filtering deferred).

Each numbered area maps to one independently-mergeable PR.

---

## 2. Independent review of the current system (confirmed from code)

All findings below were read directly from source, not from prior summaries.

### 2.1 What search actually does today

- **`TranscriptService.semanticSearch` (lines 72–104)** runs a **dual-pass**:
  - Pass 1 embeds the raw query and calls `searchByEmbedding`.
  - Pass 2 calls `openAIClient.expandSearchQuery` (a `gpt-4o-mini` call), embeds
    the expanded phrase, calls `searchByEmbedding` again.
  - Results merge into a `LinkedHashMap` via **`putIfAbsent`** — i.e. dedup by
    **insertion order**, not by score. Pass-1 hits always win ties; pass-2 only
    appends new IDs. `candidateLimit = clamp(limit*3, 25, 150)`.
- **`UserTranscriptRepositoryImpl.searchByEmbedding` (lines 116–219)** is a single
  native CTE:
  - **Recall (WHERE):** keep a row if `embedding <=> query < 0.70` **OR** the FTS
    `@@ websearch_to_tsquery` matches.
  - **Rank (ORDER BY):** `vector_distance - LEAST(text_rank*0.25, 0.35)
    - exact_match_boost`, tiebreak `vector_distance` ascending. `exact_match_boost`
    is a flat `0.15` when the concatenated text `ILIKE %query%`.
  - Returns ordered IDs; a JPQL query rehydrates the entities per user.

### 2.2 Genuinely broken / fragile

1. **The V26 FTS GIN index is dead — it is never used.**
   V26 indexes `to_tsvector('english', col1 || ' ' || col2 || …)` using `||`.
   The query computes `to_tsvector('english', concat_ws(' ', col1, col2, …))`.
   Postgres only uses an expression index when the indexed expression is
   **structurally identical** to the query expression; `||` and `concat_ws` are
   different expressions. **Result: every search seq-scans `base_transcripts` and
   recomputes `to_tsvector` per row** over title + description +
   `structured_content` + the *full transcript*. Works at current scale, falls off
   a cliff as data grows. This is the single biggest confirmed defect and was
   **not** in the prior summary.

2. **Magic constants are a symptom, not the root cause.** `vector_distance`
   (0–2, lower = better) and `ts_rank_cd` (unbounded, higher = better) are on
   **incomparable scales**, fused by subtraction. `0.25 / 0.35 / 0.15 / 0.70`
   are all attempts to beat one scale into the other. The fix is not "better
   constants" — it is "stop arithmetic-fusing incomparable scales."

3. **Dual-pass dedup ignores score.** `putIfAbsent` lets a weak pass-1 hit
   outrank a strong pass-2 hit, discarding per-pass ordering quality.

4. **No HNSW `ef_search` tuning** anywhere; the `< 0.70` distance prefilter can
   interact poorly with HNSW's approximate recall.

### 2.3 Working — leave alone

- pgvector HNSW embedding column + index (V25).
- `VideoService.buildEmbeddingInput` — rich labeled document already feeds
  tags/topics/entities into the embedding via `structured_content`.
- Canonical dedup, per-user rehydration, category-scoping.

### 2.4 Is RRF the right call? Yes.

RRF fuses by **rank position** (`Σ 1/(k + rank_i)`), never raw scores, so the
incomparable-scale problem (2.2 #2) disappears and the magic constants collapse to
a single well-established knob (`k ≈ 60`). It also cleanly subsumes the dual-pass:
the vector list, the FTS list, and the expanded-query lists all become **input
lists to one RRF fuse**, replacing the order-dependent `putIfAbsent`. The prior
session's direction (RRF + tag filtering + snippets) is sound — with the one
correction that **fixing the dead FTS index is a prerequisite**, because RRF needs
a fast, separate FTS ranked list.

---

## 3. Key decisions (locked)

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| a | RRF vs formula | **Replace formula outright** with RRF, backed by a golden-set ordering test | Fixes the incomparable-scale root cause; one knob (`k`) instead of four constants |
| b | Tag storage | **Dedicated `tags text[]` + GIN** (not JSONB-only) | Clean exact `&&` filtering; idiomatic; JSONB path can't be cleanly index-filtered |
| c | Entity scope | `entities jsonb` (people/products/places) + GIN, **display-only in v1** | Column lands ready; filtering deferred to avoid over-scope |
| d | Snippets | `ts_headline` over `generated_title + structured_content` (**not** full transcript) | Cheap "why this matched"; full transcript too slow/noisy |
| e | `topic` field | **Merge into `tags`** | Today `topic` is a single string present in only 5 of 10 builders — inconsistent, low value as its own column |
| — | FTS index style | **`GENERATED ALWAYS AS (...) STORED` tsvector column** + GIN | Eliminates the expression-drift bug class that killed V26; index can never silently stop matching |
| — | RRF rollout | **Replace + golden-set test** (no feature flag) | Simpler single code path; test gives confidence |
| — | Tag backfill | **Parse-only first** (read existing `structured_content`, no LLM) | Immediate value at zero OpenAI cost; full re-extract deferred |

### 3.1 Current prompt output shape (confirmed)

- **`tags`**: a `string[]` present in **every** builder (cooking, beauty, fitness,
  finance, tech, education, travel, entertainment, lifestyle, general). This is the
  durable signal to promote.
- **`topic`**: a **single string**, present only in finance/tech/education/travel/
  lifestyle/general; **absent** from cooking/beauty/fitness/entertainment → merged
  into `tags` (decision e).
- **`entities`**: **not currently produced**. Net-new in PR4's prompt update.

---

## 4. PR stack

Each PR is independently mergeable and produces testable value.

### PR1 — RRF fusion + index-backed FTS (V30)  *(stages 2–3)*

**Delivers:** the core ranking rewrite plus a working FTS index.

- **V30 migration:**
  - Add `search_tsv tsvector GENERATED ALWAYS AS (to_tsvector('english',
    concat_ws(' ', title, generated_title, description, structured_content::text,
    transcript))) STORED` on `base_transcripts`.
  - `CREATE INDEX ... USING gin (search_tsv)`.
  - `DROP INDEX IF EXISTS idx_base_transcripts_search_text` (the dead V26 index).
- **Rewrite `searchByEmbedding`** into independent ranked CTEs fused by RRF:
  - `vec` CTE: top-N by `embedding <=> :q` ascending.
  - `fts` CTE: top-N by `ts_rank_cd(search_tsv, websearch_to_tsquery(:q))` desc,
    filtered by `search_tsv @@ ...`.
  - Fuse: `score = Σ 1/(:k + rank)` across lists (`k = 60`), order by fused score.
  - Remove `0.25 / 0.35 / 0.15`; keep an explicit recall cap (top-N per list).
- **Dual-pass → multi-list RRF:** expanded-query vector/FTS lists become
  additional RRF inputs; delete the `putIfAbsent` insertion-order merge.
- **Tests:** golden-set ordering test (fixed transcript fixtures + expected rank
  order) asserting RRF output; keep existing dual-pass unit tests updated.
- **No API change.** Pure search-quality + perf.

### PR2 — Tag storage + deterministic filtering (V31)  *(stage 4)*

**Delivers:** first-class `tags`, populated on ingest, filterable via API.

- **V31 migration:** `ADD COLUMN tags text[]` + `CREATE INDEX ... USING gin (tags)`.
- **Ingest:** parse `structured_content.tags` (lowercase, trim, dedup) + merge the
  single-string `topic` into `tags`; write to the column.
- **Parse-only backfill** admin endpoint: reads existing `structured_content`,
  writes `tags`, **no LLM calls**.
- **API:** optional `tags` param on search/list → `tags && ARRAY[:tags]`. Filtering
  is applied **independently of** RRF recall (deterministic, not a recall source).
- **Tests:** filter correctness, normalization, empty-param = current behaviour.
- Additive: absent `tags` param ⇒ unchanged behaviour.

### PR3 — Snippets / "why this matched" (no migration)  *(stage 5)*

**Delivers:** a highlight string per result.

- Add `ts_headline('english', concat_ws(' ', generated_title,
  structured_content::text), websearch_to_tsquery(:q))` to the search projection
  (**not** the full transcript).
- New `snippet` field on the search-result DTO; iOS renders "matched on…".
- **Tests:** snippet present on text matches, gracefully empty on pure-vector hits.
- Additive response field only.

### PR4 — Entity extraction storage (V32)  *(display-only v1)*

**Delivers:** `entities` column + prompt updates + API display.

- **V32 migration:** `ADD COLUMN entities jsonb` + `CREATE INDEX ... USING gin (entities)`.
- **Prompts:** add a consistent `entities { people[], products[], places[] }` block
  to the builders; parse into the column on ingest.
- **API:** expose `entities` in the DTO. **Entity filtering endpoint deferred.**
- **Backfill:** parse-only where entities already present; full re-extract deferred
  (largest OpenAI cost item — intentionally last).
- Additive.

**Ordering rationale:** PR1 fixes the actually-broken thing (fusion + dead index)
and delivers the biggest standalone quality/perf win. PR2/PR3 are independent
stage-4/stage-5 features layering cleanly on top. PR4 is the largest LLM-cost item,
so it ships last and display-only.

---

## 5. Risks & stale assumptions

- **Stale migration numbers.** The superseded spec references V28; those are taken
  by the parallel stack. This stack is V30/V31/V32. Re-check the latest version on
  disk before writing each migration.
- **Generated-column cost.** `search_tsv GENERATED … STORED` is recomputed on every
  row update and adds storage. Acceptable for an append-mostly transcript table; do
  not add it to a hot, high-churn table.
- **HNSW + RRF recall.** RRF only ranks what each list returns. Set the per-list
  recall cap (top-N) generously and consider tuning `ef_search` if vector recall
  looks thin. Out of scope to fully tune here, but flagged.
- **Backfill cost asymmetry.** Parse-only backfill (PR2) is free; full re-extract
  (PR4 entities) is OpenAI-cost-per-transcript and scales with corpus size — keep
  it last and gate it behind an explicit admin action.
- **Generated column + `structured_content::text`.** If `structured_content` is
  large, the generated tsvector grows; this is the same content the dead V26 index
  already covered, so no regression, but watch index size after backfill.

## 6. Non-goals (explicit)

- Chunked / per-section embeddings (large lift, premature at current scale).
- Click/open feedback ranking signals (no data).
- Android client support.
- Changes to the yt-dlp / Whisper ingestion pipeline.
- Entity *filtering* endpoint (column lands in PR4; querying deferred).

## 7. Validation notes

- **Query-plan validation was analytical, not live.** No Postgres was running
  locally and spinning one up would have triggered Flyway migrations, so the
  seq-scan finding (§2.2 #1) was derived from comparing the V26 index expression
  (`||`) against the query expression (`concat_ws`). **Before implementing PR1,
  capture a real `EXPLAIN ANALYZE` on a seeded DB** to quantify the seq-scan cost
  and confirm the generated-column index is chosen.
- Evidence captured: the fusion formula (§2.1), the prompt output shape (§3.1),
  and the Flyway state (latest on disk = V27; V28/V29 reserved for the parallel
  stack).

