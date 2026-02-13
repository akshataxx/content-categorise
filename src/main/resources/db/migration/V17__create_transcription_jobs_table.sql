-- V17__create_transcription_jobs_table.sql
-- Create durable transcription job queue table to replace in-memory ThreadPool queue

CREATE TABLE transcription_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    video_url           VARCHAR(2048) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    error_message       TEXT,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMP WITH TIME ZONE,
    base_transcript_id  UUID REFERENCES base_transcripts(id),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- User's job list
CREATE INDEX idx_jobs_user_id ON transcription_jobs (user_id);
