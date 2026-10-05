SET client_min_messages = warning;
\set U '''11111111-1111-1111-1111-111111111111'''
CREATE TABLE spike_c AS SELECT row_number() OVER (ORDER BY name_key) AS i, id FROM collections WHERE user_id = :U;
-- cursor at the 4,950th row of each collection (deep page)
CREATE TABLE spike_cursor AS
SELECT collection_id, sort_key, user_transcript_id FROM (
  SELECT collection_id, sort_key, user_transcript_id,
         row_number() OVER (PARTITION BY collection_id ORDER BY sort_key, user_transcript_id) rn
  FROM collection_items) x WHERE rn = 4950;
-- B arrays: current order and rotated by one, alternated so every call rewrites all 5,000 positions
CREATE TABLE spike_arrays AS
WITH o AS (SELECT array_agg(user_transcript_id ORDER BY position) a FROM collection_items_b)
SELECT 0 AS k, a AS ids FROM o UNION ALL SELECT 1, a[2:5000] || a[1] FROM o;
CREATE SEQUENCE spike_seq;
-- 16 extra saved transcripts for U, not in any collection (race candidates)
INSERT INTO base_transcripts (id, video_url, transcript, title, platform)
SELECT gen_random_uuid(), 'https://example.com/extra/'||g, 'x', 'Extra '||g, 'YOUTUBE' FROM generate_series(1,16) g;
CREATE TABLE spike_cand AS
WITH ins AS (INSERT INTO user_transcripts (id, user_id, base_transcript_id)
  SELECT gen_random_uuid(), :U, id FROM base_transcripts WHERE video_url LIKE 'https://example.com/extra/%' RETURNING id)
SELECT row_number() OVER () - 1 AS client, id FROM ins;
CREATE OR REPLACE FUNCTION spike_random_move(cid uuid) RETURNS int AS $$
DECLARE ids uuid[]; m int; a int;
BEGIN
  SELECT array_agg(user_transcript_id) INTO ids FROM (SELECT user_transcript_id FROM collection_items
    WHERE collection_id = cid ORDER BY sort_key, user_transcript_id LIMIT 50) f;
  m := 1 + floor(random() * 50)::int; a := 1 + floor(random() * 50)::int;
  IF a = m THEN a := CASE WHEN m = 1 THEN 2 ELSE m - 1 END; END IF;
  RETURN spike_move(cid, ids[m], ids[a]);
END $$ LANGUAGE plpgsql;
CREATE OR REPLACE FUNCTION spike_remove(cid uuid, ut uuid) RETURNS int AS $$
DECLARE n int;
BEGIN
  PERFORM 1 FROM collections WHERE id = cid FOR UPDATE;
  DELETE FROM collection_items WHERE collection_id = cid AND user_transcript_id = ut;
  GET DIAGNOSTICS n = ROW_COUNT; RETURN n;
END $$ LANGUAGE plpgsql;
