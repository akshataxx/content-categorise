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
    max_retries         INTEGER NOT NULL DEFAULT 3,
    next_retry_at       TIMESTAMP WITH TIME ZONE,
    base_transcript_id  UUID REFERENCES base_transcripts(id),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_jobs_retry CHECK (retry_count >= 0 AND retry_count <= max_retries)
);

-- Poller query: find next eligible PENDING job
CREATE INDEX idx_jobs_status_next_retry ON transcription_jobs (status, next_retry_at, created_at);

-- User's job list
CREATE INDEX idx_jobs_user_id ON transcription_jobs (user_id);

-- Deduplication: check for existing active job for same user + URL
CREATE INDEX idx_jobs_user_video_status ON transcription_jobs (user_id, video_url, status);
