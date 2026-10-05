# Spike: reorder at 5,000 and limits under concurrency (Collections, Stage 5)

Run 2026-10-05 by the agent for apushpavannan (FL). Postgres 15.19 (`pgvector/pgvector:pg15`, aarch64) in Docker on the developer Mac (12 CPUs), all 27 real migrations from main @ 86f6dc5 plus `01_schema.sql` (candidate V28). Seed: one person with 5,000 saved transcripts (8 KB text each, a guess at size), 200 collections each holding all 5,000 (1,000,000 memberships); 20 other people with 5,000 saved transcripts each (105,000 rows). Latency is pgbench per-transaction time over a local socket, commit included; it excludes Spring, Hibernate, JSON, HTTP and the network.

Pass bar (apushpavannan, 5 October 2026): p90 under 500 ms on the server for list, page, add, remove and reorder at 5,000, including the occasional full renumber.

| Measurement | n | p50 ms | p90 ms | p99 ms | Rows written | Pass |
|---|---|---|---|---|---|---|
| List 200 collections with counts counted from memberships (as seeded, autovacuum had run) | 50 | 51.6 | 53.3 | 70.3 | 0 | yes |
| Same, after 1,000 moves and 100 renumbers | 50 | 56.4 | 64.9 | 100.0 | 0 | yes |
| Same, after VACUUM ANALYZE | 50 | 41.2 | 43.1 | 68.3 | 0 | yes |
| List with a stored count column instead (A2) | 200 | 0.3 | 0.3 | 0.3 | 0 | yes |
| First page of 50 (+1 lookahead), keyset, with 8 KB transcript text | 300 | 0.5 | 0.5 | 0.7 | 0 | yes |
| Deep page (after row 4,950) | 300 | 8.6 | 10.0 | 12.6 | 0 | yes |
| Deep page after the write tests | 300 | 10.6 | 13.2 | 17.5 | 0 | yes |
| A: move one row among the first 50 loaded, collection of 5,000 | 1,000 | 0.2 | 0.3 | 0.4 | 1 each (n_tup_upd) | yes |
| A: forced renumber of all 5,000 keys | 100 | 26.8 | 44.6 | 57.4 | 5,000 | yes |
| B: full-list rewrite of 5,000 positions (array already on the server) | 100 | 20.5 | 39.5 | 52.3 | 5,000 | yes (database side only) |
| Add near the limit with the collection row locked, count then insert (outcome not checked per call: some calls may have been refusals at 5,000; the count of ~5,000 rows runs either way) | 300 | 3.7 | 7.0 | 7.9 | 0 or 1 | yes |
| Remove with the collection row locked | 300 | 0.4 | 0.8 | 1.4 | 1 | yes |

Gap exhaustion (A, `06_gap_exhaustion.sql`): 40 different rows moved, one at a time, to directly after the same anchor. Move 33 triggered one renumber (4,112 rows written); every move left the moved row as the anchor's successor (0 wrong); 5,000 rows, 5,000 distinct keys after.

Concurrency (`07_conc.sh`, pgbench, every client released together, a 0.2 s pause between count and insert to widen the race):

| Race | Without lock | With lock |
|---|---|---|
| 16 adds of different transcripts to a collection at 4,990 | ended at **5,006** (limit broken) | ended at **5,000**; 6 refused as full |
| 8 creates of different names at 199 collections | ended at **207** (limit broken) | ended at **200** |
| 8 creates of "Recipes" in 8 spellings (case, spaces) | 1 row (case-folded unique key + ON CONFLICT DO NOTHING) | 1 row |

No transaction failed in any race (no error, so nothing that would become a 500).

Deletion (`08_cascades.sql`, one transaction each):
- R10 bulk delete of 100 saved transcripts, each in ~159 collections: DELETE 20.1 ms + commit 7.7 ms; 15,897 memberships removed by cascade.
- R11 account delete with ~779,000 memberships: collections DELETE (cascade) 422.8 ms, saved transcripts 606.0 ms, user 1.8 ms, commit 0.7 ms; ~1.03 s in total; 0 memberships left.

## What we tested, and what we didn't

Tested: the database cost of every N2 call at the limits; A's move, its gap exhaustion and its worst-case renumber; B's full rewrite; the row-lock mechanism for both limits and the case-folded name key under concurrent requests; both deletion paths at the limits, through real FK cascades.

Not tested, and who carries it:
- Spring, Hibernate, JSON and HTTP time, and the app's alias lookup per item on a page: apushpavannan (FL), at build with a timed run on the deployed backend (Estimate release row).
- B's 200 KB upload and the extra call to fetch all 5,000 IDs before a drag (only the database rewrite was timed).
- Production hardware (the droplet), a shared pool of 10 with the job poller, and 8 concurrent list calls (that run's log was mixed with another, so it is discarded).
- The app's account deletion path: it deletes saved transcripts row by row through Hibernate, slower than the single DELETE timed here.
- The lock-order deadlock the arena judge found between create-with-initial-transcript and account deletion (static analysis only).
- Hibernate's test schema emitting the cascades and the unique key (S2 in the candidate designs).
- Real transcript sizes (8 KB is a guess).

## Blast radius: existing deletion paths with the new tables (run 2026-10-05)

`src/test/java/com/app/categorise/spike/CollectionsCascadeSpikeIT.java`: a Spring Boot test on `pgvector/pgvector:pg15` with Flyway on (all real migrations V1–V27 plus the spike V28, kept under src/test/resources/db/spike so Flyway never runs it outside this test), calling the real, unchanged `TranscriptService.deleteTranscripts` and `UserService.deleteAccount`.

| Run | Result |
|---|---|
| V28 with `ON DELETE CASCADE` on collection_items → user_transcripts | 2 tests, 0 failures: bulk delete removes the memberships and keeps both collections; account delete succeeds, removes the person's collections and memberships, leaves another person's untouched |
| Negative control: the same FK without the cascade | 2 errors, both `DataIntegrityViolationException` (`violates foreign key constraint "collection_items_user_transcript_id_fkey"`), raised at commit, not wrapped as `TranscriptDeletionException`: the app would return a 500 |

So the existing deletion code needs no change for R10/R11 as long as the membership FK cascades in the database; account delete works even without its own collections line, because `users ON DELETE CASCADE` and the transcript cascade cover it. Hibernate's test schema (Flyway off) was not used here, so whether `ddl-auto=create` emits the cascade is still untested.
