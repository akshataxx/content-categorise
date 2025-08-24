-- V7: Add rate limiting tables for video transcription
-- This migration adds tables to support rate limiting functionality

-- Table to store rate limit configurations per user
CREATE TABLE user_rate_limits (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    transcripts_per_minute_limit INTEGER NOT NULL DEFAULT 5,
    transcripts_per_day_limit INTEGER NOT NULL DEFAULT 100,
    total_transcripts_limit INTEGER NOT NULL DEFAULT 10000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_rate_limits_user_id 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Table to track usage for windowed rate limits (per minute and per day)
-- Note: Total transcript count is calculated from user_transcripts table directly
CREATE TABLE user_rate_limit_tracking (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_type VARCHAR(20) NOT NULL, -- 'MINUTE' or 'DAY'
    request_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_rate_limit_tracking_user_id 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    -- Ensure one record per user per window
    UNIQUE(user_id, window_start, window_type),
    
    -- Validate window_type values
    CONSTRAINT chk_window_type CHECK (window_type IN ('MINUTE', 'DAY'))
);

-- Indexes for performance
CREATE INDEX idx_user_rate_limits_user_id ON user_rate_limits(user_id);
CREATE INDEX idx_user_rate_limit_tracking_user_window ON user_rate_limit_tracking(user_id, window_type, window_start);
CREATE INDEX idx_user_rate_limit_tracking_cleanup ON user_rate_limit_tracking(window_type, window_start);
