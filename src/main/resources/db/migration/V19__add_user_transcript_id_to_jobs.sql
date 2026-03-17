-- Add user_transcript_id to transcription_jobs so the iOS app can navigate
-- directly from an Activity item to the correct user transcript endpoint.
ALTER TABLE transcription_jobs ADD COLUMN user_transcript_id UUID;
