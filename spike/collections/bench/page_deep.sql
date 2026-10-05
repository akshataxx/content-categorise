\set c random(2, 200)
SELECT i.user_transcript_id, b.title, b.transcript, b.platform, b.duration
FROM spike_cursor k JOIN collection_items i ON i.collection_id = k.collection_id
 AND (i.sort_key, i.user_transcript_id) > (k.sort_key, k.user_transcript_id)
JOIN user_transcripts ut ON ut.id = i.user_transcript_id
JOIN base_transcripts b ON b.id = ut.base_transcript_id
WHERE k.collection_id = (SELECT id FROM spike_c WHERE i = :c)
ORDER BY i.sort_key, i.user_transcript_id LIMIT 51;
