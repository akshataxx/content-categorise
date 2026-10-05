\set c random(2, 200)
\set n random(1, 5000)
SELECT spike_remove((SELECT id FROM spike_c WHERE i = :c), (SELECT user_transcript_id FROM collection_items WHERE collection_id = (SELECT id FROM spike_c WHERE i = :c) ORDER BY sort_key OFFSET :n - 1 LIMIT 1));
