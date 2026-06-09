<claude-mem-context>
# Memory Context

# [content-categorise] recent context, 2026-05-22 12:11pm GMT+10

Legend: 🎯session 🔴bugfix 🟣feature 🔄refactor ✅change 🔵discovery ⚖️decision
Format: ID TIME TYPE TITLE
Fetch details: get_observations([IDs]) | Search: mem-search skill

Stats: 46 obs (20,575t read) | 325,836t work | 94% savings

### May 5, 2026
137 9:58a ⚖️ GCP to DigitalOcean Migration — Backend &amp; Postgres Cost Reduction
138 9:59a ⚖️ Backend Migration: GCP to DigitalOcean for Cost Reduction
139 " ✅ GCP to DigitalOcean migration — deployment config and runbook
140 10:00a ⚖️ Migrate Backend & Postgres from GCP VM to DigitalOcean Droplet
141 10:01a ⚖️ Backend Migration: GCP VM → DigitalOcean VM (Cost Reduction)
142 " ⚖️ GCP to DigitalOcean Migration — Backend + Postgres Cost Optimization
143 " ⚖️ GCP to DigitalOcean VM Migration — Backend + PostgreSQL
144 11:10a 🔵 Backend .env File Location on GCP VM — Requires sudo su apushpavannan
S28 categorise app startup fails — pgvector extension missing from PostgreSQL 14 (May 5, 11:10 AM)
S13 Backend .env File Location on GCP VM — Requires sudo su apushpavannan (May 5, 11:10 AM)
### May 8, 2026
180 9:37a 🔵 content-categorise transcript domain structure mapped
181 " 🔵 content-categorise backend architecture fully mapped for RAG search planning
182 9:38a 🔵 No existing search infrastructure in content-categorise — zero FTS or vector columns
183 " ⚖️ RAG Search Design for Transcript Lookup — Implementation Planning
### May 14, 2026
213 11:19a 🔵 categorise app startup fails — pgvector extension missing from PostgreSQL 14
S30 SemanticSearch similarity threshold filter added to searchByEmbedding (May 14, 11:19 AM)
216 11:31a 🔵 Semantic search returns cross-category results — embeddings ignore content type
217 " 🔵 content-categorise backend — Spring Boot Java project structure mapped
218 11:32a 🔵 Semantic search bug root cause traced — no category filter in searchByEmbedding SQL
219 12:02p 🟣 SemanticSearch similarity threshold filter added to searchByEmbedding
S31 Cosine distance calculation ownership — Postgres vs LLM, and similarity threshold tuning (May 14, 12:02 PM)
S32 Semantic search similarity threshold tightened to fix broad keyword misses (May 14, 12:07 PM)
220 12:10p 🔴 Semantic search similarity threshold tightened to fix broad keyword misses
S33 Improving semantic search accuracy (currently ~0.8) in content-categorise — whether more categories and structuredContent types would help (May 14, 12:10 PM)
S34 Add category-scoped search to transcript search endpoint so results are filtered when browsing inside a category (May 14, 12:15 PM)
221 12:15p 🔵 content-categorise — embedding pipeline in VideoService
222 1:16p 🔵 TranscriptController — existing search and filter endpoints mapped
223 " 🟣 Category-scoped semantic search added to TranscriptController and repository layer
S43 Embedding input now uses structuredContent over raw transcript (May 14, 1:16 PM)
### May 19, 2026
235 10:39a ⚖️ TranscribeAssistant — AI label generation + vector search improvement proposal
236 10:52a 🟣 Embedding input now uses structuredContent over raw transcript
237 11:39a 🔵 content-categorise — BaseTranscript dedup architecture and controller inventory
238 11:40a 🔵 content-categorise — VideoService embedding generation for base_transcripts
239 " 🔵 content-categorise — VideoService pipeline: download → audio → Whisper → transcript
240 " 🟣 BaseTranscriptRepository — added findAllByStructuredContentIsNotNull() query
### May 21, 2026
262 1:05p 🔵 Current structured content extraction limited to 2 category types; general prompt lacks semantic metadata
263 1:06p ⚖️ Search Relevance: Richer Structured Content via Category-Specific Prompts
264 " ⚖️ Search Relevance Improvement Plan — Richer Structured Content per Category
265 " 🟣 Phase 1–3 Search Relevance Implemented — 7 New Prompt Builders, Re-extract Endpoint, Threshold Tightened
266 1:09p ⚖️ Search Relevance Improvement Plan — Richer Structured Content by Category
S48 Search Relevance Improvement Plan — Richer Structured Content by Category (May 21, 1:09 PM)
### May 22, 2026
267 11:12a 🔵 content-categorise — semantic search architecture mapped for relevance investigation
268 " 🔵 TranscribeAssistant — semantic search not returning all relevant results
269 11:13a 🔵 Semantic search implementation in content-categorise — embedding pipeline and pgvector query
270 11:14a 🔵 TranscribeAssistant semantic search — repository layer architecture
271 11:15a 🟣 Hybrid semantic search — vector + full-text + exact match boosting
272 11:16a 🟣 semanticSearch dual-pass strategy — original query + expanded query with deduplication
273 " 🟣 Embedding input enriched with category, creator, and labeled fields
274 11:17a 🟣 Unit tests added for dual-pass semantic search in TranscriptServiceTest
275 " ✅ semanticSearch candidateLimit upper bound raised from 50 to 150
276 " ✅ Search API defaults and limits raised — default limit 10→25, max 50→100
277 11:18a ✅ CRLF line endings stripped from all modified Java source files
278 11:19a 🟣 Flyway migration V26 adds GIN full-text search index on base_transcripts
279 " 🔵 Category name included in ILIKE boost but excluded from GIN-indexed full-text WHERE clause
280 11:26a 🔴 TranscriptServiceTest — CategoryEntity ID injection fixed via reflection

Access 326k tokens of past work via get_observations([IDs]) or mem-search skill.
</claude-mem-context>