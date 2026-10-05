\set c random(2, 200)
SELECT spike_add((SELECT id FROM spike_c WHERE i = :c), (SELECT ut.id FROM user_transcripts ut WHERE ut.user_id = '11111111-1111-1111-1111-111111111111' AND NOT EXISTS (SELECT 1 FROM collection_items x WHERE x.collection_id = (SELECT id FROM spike_c WHERE i = :c) AND x.user_transcript_id = ut.id) LIMIT 1), true, 0);
