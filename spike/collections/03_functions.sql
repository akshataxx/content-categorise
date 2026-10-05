-- Option A move: place :moved directly after :after (NULL = top). Locks the collection row.
-- Returns the number of membership rows written (1 normally, up to 5,000 on a renumber).
CREATE OR REPLACE FUNCTION spike_move(cid uuid, moved uuid, after_id uuid) RETURNS int AS $$
DECLARE lo bigint; hi bigint; n int; step constant bigint := 4294967296;
BEGIN
  PERFORM 1 FROM collections WHERE id = cid FOR UPDATE;
  IF after_id IS NULL THEN
    SELECT min(sort_key) INTO hi FROM collection_items WHERE collection_id = cid AND user_transcript_id <> moved;
    lo := hi - 2 * step;
  ELSE
    SELECT sort_key INTO lo FROM collection_items WHERE collection_id = cid AND user_transcript_id = after_id;
    SELECT sort_key INTO hi FROM collection_items
      WHERE collection_id = cid AND user_transcript_id <> moved AND sort_key > lo ORDER BY sort_key LIMIT 1;
    IF hi IS NULL THEN hi := lo + 2 * step; END IF;
  END IF;
  IF hi - lo > 1 THEN
    UPDATE collection_items SET sort_key = lo + (hi - lo) / 2 WHERE collection_id = cid AND user_transcript_id = moved;
    RETURN 1;
  END IF;
  -- gap exhausted: renumber the whole collection with the moved row after the anchor
  WITH ordered AS (
    SELECT user_transcript_id,
           row_number() OVER (ORDER BY CASE WHEN user_transcript_id = moved THEN lo ELSE sort_key END,
                                       (user_transcript_id = moved)) AS rn
    FROM collection_items WHERE collection_id = cid)
  UPDATE collection_items ci SET sort_key = o.rn * step FROM ordered o
  WHERE ci.collection_id = cid AND ci.user_transcript_id = o.user_transcript_id AND ci.sort_key <> o.rn * step;
  GET DIAGNOSTICS n = ROW_COUNT; RETURN n;
END $$ LANGUAGE plpgsql;

-- Forced worst-case renumber: every row's key changes (alternates spacing each call).
CREATE OR REPLACE FUNCTION spike_renumber_all(cid uuid) RETURNS int AS $$
DECLARE n int; base bigint;
BEGIN
  PERFORM 1 FROM collections WHERE id = cid FOR UPDATE;
  SELECT CASE WHEN min(sort_key) % 2 = 0 THEN 4294967297 ELSE 4294967296 END INTO base
    FROM collection_items WHERE collection_id = cid;
  WITH ordered AS (SELECT user_transcript_id, row_number() OVER (ORDER BY sort_key, user_transcript_id) rn
                   FROM collection_items WHERE collection_id = cid)
  UPDATE collection_items ci SET sort_key = o.rn * base FROM ordered o
  WHERE ci.collection_id = cid AND ci.user_transcript_id = o.user_transcript_id;
  GET DIAGNOSTICS n = ROW_COUNT; RETURN n;
END $$ LANGUAGE plpgsql;

-- Option B: full-list rewrite from an ordered id array (what the existing iOS PATCH /order implies).
CREATE OR REPLACE FUNCTION spike_reorder_full(cid uuid, ids uuid[]) RETURNS int AS $$
DECLARE n int;
BEGIN
  PERFORM 1 FROM collections WHERE id = cid FOR UPDATE;
  UPDATE collection_items_b b SET position = o.ord
  FROM unnest(ids) WITH ORDINALITY o(id, ord)
  WHERE b.collection_id = cid AND b.user_transcript_id = o.id AND b.position <> o.ord;
  GET DIAGNOSTICS n = ROW_COUNT; RETURN n;
END $$ LANGUAGE plpgsql;

-- Add with the collection row locked: count, then insert. pause widens the race window for the test.
CREATE OR REPLACE FUNCTION spike_add(cid uuid, ut uuid, use_lock boolean, pause float8) RETURNS text AS $$
DECLARE cnt int;
BEGIN
  IF use_lock THEN PERFORM 1 FROM collections WHERE id = cid FOR UPDATE; END IF;
  IF EXISTS (SELECT 1 FROM collection_items WHERE collection_id = cid AND user_transcript_id = ut) THEN RETURN 'exists'; END IF;
  SELECT count(*) INTO cnt FROM collection_items WHERE collection_id = cid;
  PERFORM pg_sleep(pause);
  IF cnt >= 5000 THEN RETURN 'full'; END IF;
  INSERT INTO collection_items (collection_id, user_transcript_id, sort_key)
  SELECT cid, ut, coalesce(max(sort_key), 0) + 4294967296 FROM collection_items WHERE collection_id = cid;
  RETURN 'added';
END $$ LANGUAGE plpgsql;

-- Create with the person's users row locked (FOR NO KEY UPDATE), limit 200, case-folded unique key.
CREATE OR REPLACE FUNCTION spike_create(uid uuid, nm text, use_lock boolean, pause float8) RETURNS text AS $$
DECLARE cnt int; k text := lower(btrim(nm)); n int;
BEGIN
  IF use_lock THEN PERFORM 1 FROM users WHERE id = uid FOR NO KEY UPDATE; END IF;
  SELECT count(*) INTO cnt FROM collections WHERE user_id = uid;
  PERFORM pg_sleep(pause);
  IF cnt >= 200 THEN RETURN 'limit'; END IF;
  INSERT INTO collections (id, user_id, name, name_key) VALUES (gen_random_uuid(), uid, btrim(nm), k)
  ON CONFLICT (user_id, name_key) DO NOTHING;
  GET DIAGNOSTICS n = ROW_COUNT;
  RETURN CASE WHEN n = 1 THEN 'created' ELSE 'duplicate' END;
END $$ LANGUAGE plpgsql;
