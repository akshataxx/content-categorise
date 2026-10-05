-- Move 40 different rows, one at a time, to directly after the same anchor row.
-- After each move the moved row must be the anchor's immediate successor; record rows written.
DO $$
DECLARE cid uuid := (SELECT id FROM spike_c WHERE i = 5); anchor uuid; mv uuid; w int; nxt uuid;
        renumbers int := 0; wrong int := 0; i int;
BEGIN
  SELECT user_transcript_id INTO anchor FROM collection_items WHERE collection_id = cid ORDER BY sort_key, user_transcript_id LIMIT 1;
  FOR i IN 1..40 LOOP
    SELECT user_transcript_id INTO mv FROM collection_items WHERE collection_id = cid
      ORDER BY sort_key, user_transcript_id OFFSET 10 + i LIMIT 1;
    w := spike_move(cid, mv, anchor);
    IF w > 1 THEN renumbers := renumbers + 1; RAISE NOTICE 'move % renumbered % rows', i, w; END IF;
    SELECT user_transcript_id INTO nxt FROM collection_items WHERE collection_id = cid
      AND (sort_key, user_transcript_id) > (SELECT sort_key, user_transcript_id FROM collection_items WHERE collection_id = cid AND user_transcript_id = anchor)
      ORDER BY sort_key, user_transcript_id LIMIT 1;
    IF nxt <> mv THEN wrong := wrong + 1; END IF;
  END LOOP;
  RAISE NOTICE 'renumbers=% wrong_successor=% distinct_keys=% rows=%', renumbers, wrong,
    (SELECT count(DISTINCT sort_key) FROM collection_items WHERE collection_id = cid),
    (SELECT count(*) FROM collection_items WHERE collection_id = cid);
END $$;
