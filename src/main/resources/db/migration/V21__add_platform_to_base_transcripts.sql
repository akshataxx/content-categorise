-- Add platform tracking to base_transcripts and transcription_jobs
ALTER TABLE base_transcripts ADD COLUMN platform VARCHAR(20);
ALTER TABLE transcription_jobs ADD COLUMN platform VARCHAR(20);

-- Backfill: all existing rows were TikTok (the only supported platform before this change)
UPDATE base_transcripts SET platform = 'TIKTOK' WHERE platform IS NULL;
UPDATE transcription_jobs SET platform = 'TIKTOK' WHERE platform IS NULL;
