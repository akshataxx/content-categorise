-- One person U with 5,000 saved transcripts (8 KB text each, a guess at realistic size),
-- 200 collections, each holding all 5,000 (1,000,000 memberships). Plus 20 other people
-- with 5,000 saved transcripts each as background rows in user_transcripts.
\set U '''11111111-1111-1111-1111-111111111111'''
INSERT INTO users (id, email) VALUES (:U, 'spike@example.com');
INSERT INTO users (id, email) SELECT gen_random_uuid(), 'other'||g||'@example.com' FROM generate_series(1,20) g;
INSERT INTO base_transcripts (id, video_url, transcript, title, platform)
SELECT gen_random_uuid(), 'https://example.com/v/'||g, repeat(md5(g::text), 256), 'Video '||g, 'YOUTUBE'
FROM generate_series(1, 5000) g;
INSERT INTO user_transcripts (id, user_id, base_transcript_id)
SELECT gen_random_uuid(), :U, id FROM base_transcripts;
INSERT INTO user_transcripts (id, user_id, base_transcript_id)
SELECT gen_random_uuid(), u.id, b.id FROM users u CROSS JOIN base_transcripts b WHERE u.id <> :U;
INSERT INTO collections (id, user_id, name, name_key)
SELECT gen_random_uuid(), :U, 'Collection '||g, 'collection '||g FROM generate_series(1,200) g;
INSERT INTO collection_items (collection_id, user_transcript_id, sort_key)
SELECT c.id, ut.id, (row_number() OVER (PARTITION BY c.id ORDER BY ut.id)) * 4294967296
FROM collections c CROSS JOIN user_transcripts ut WHERE ut.user_id = :U;
-- B: one collection with dense positions
INSERT INTO collection_items_b (collection_id, user_transcript_id, position)
SELECT (SELECT id FROM collections WHERE name='Collection 1'), ut.id, row_number() OVER (ORDER BY ut.id)
FROM user_transcripts ut WHERE ut.user_id = :U;
