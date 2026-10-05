\timing on
\set U '''11111111-1111-1111-1111-111111111111'''
SELECT count(*) AS memberships_before FROM collection_items;
-- R10: bulk delete of 100 saved transcripts, each in all ~180 of U's collections
BEGIN;
DELETE FROM user_transcripts WHERE id IN (SELECT user_transcript_id FROM collection_items WHERE collection_id = (SELECT id FROM spike_c WHERE i = 2) ORDER BY sort_key LIMIT 100);
COMMIT;
SELECT count(*) AS memberships_after_bulk FROM collection_items;
-- R11: account delete. Collections first (one statement, cascades memberships), then the rest via users cascade.
BEGIN;
DELETE FROM collections WHERE user_id = :U;
DELETE FROM user_transcripts WHERE user_id = :U;
DELETE FROM users WHERE id = :U;
COMMIT;
SELECT count(*) AS memberships_after_account FROM collection_items;
