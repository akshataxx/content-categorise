\set c random(2, 200)
SELECT i.user_transcript_id, b.title, b.transcript, b.platform, b.duration
FROM collection_items i JOIN user_transcripts ut ON ut.id = i.user_transcript_id
JOIN base_transcripts b ON b.id = ut.base_transcript_id
WHERE i.collection_id = (SELECT id FROM spike_c WHERE i = :c)
ORDER BY i.sort_key, i.user_transcript_id LIMIT 51;
