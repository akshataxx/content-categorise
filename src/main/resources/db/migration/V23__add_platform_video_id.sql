-- Add canonical platform-specific video id for Tier-2 dedup.
-- Different URL forms (e.g. youtu.be/X vs youtube.com/watch?v=X) point at the
-- same underlying video; we now dedup base transcripts by (platform, platform_video_id)
-- in addition to the existing exact URL match in `video_url`.
ALTER TABLE base_transcripts
    ADD COLUMN platform_video_id VARCHAR(255);

-- Partial unique index: only enforce uniqueness on rows that have both fields.
-- Existing rows (created before this change) are left with NULL and continue
-- to dedup via the URL-based path.
CREATE UNIQUE INDEX uq_base_transcripts_platform_video_id
    ON base_transcripts (platform, platform_video_id)
    WHERE platform IS NOT NULL AND platform_video_id IS NOT NULL;
