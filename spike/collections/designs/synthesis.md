# Arena synthesis note: Collections design sketch, 2026-10-05

Candidates: C1 (parent model), C2 (gpt-6.1-sol @ xhigh). Cross-judge: gpt-6.1-sol @ xhigh. No dropouts. Both runners confirmed they read none of the older files found in /tmp/arena-collections/ (off limits).

Convergence: both chose the same whole shape: a join table of memberships on user_transcripts with DB cascades, a hidden sparse sort key, a relative "move" request naming only loaded rows, keyset paging, row locks for limits and names, a case-folded unique name key, error codes on ErrorResponse. Both rejected dense full-list replacement (the existing iOS PATCH /order). Convergence is the agreement signal.

They differ in invariant enforcement:
- C1 (light): BIGINT key spaced 2^32, renumber on gap exhaustion; derived counts; per-owner users-row lock and per-collection row lock; `after` cursor; accepts that another device's move across the cursor can duplicate or drop a row.
- C2 (strict): NUMERIC rank, deferrable unique; capacity slots 1..200 and 1..5,000 as DB-enforced caps; trigger-maintained count and revision; authenticated cursor bound to revision (stale page → 409, app reloads); atomic multi-collection membership delta from the picker; summary rows instead of full transcripts on pages; composite ownership FKs; ordered lock protocol extended into transcript and account deletion.

Rubric scores (judge): C1 2,3,3,3,3,4; C2 4,5,4,4,5,3. Judge base: C2. Parent read agrees with the scores. The rubric doesn't weigh build cost against the 4-dev-week appetite; C2 obliges materially more (triggers, slots, cursor secret, delta endpoint, lock protocol in two existing delete paths). That trade goes to the people, not the picker: recorded as two variants of Option A.

Grafts adopted into both variants: C2's revision-bound pages (reject C1's accepted missing-row paging, judge); C1's BIGINT spacing in place of NUMERIC (judge); C1's stale-visibility-map and bloat checks in the spike.
Rejected: C1's "no lock-order cycle" claim: static analysis shows a deadlock path against account deletion (UserService.java:120,131); either variant must take locks in one order in deleteAccount.
Verification: not run (Phase F belongs to the spike).
