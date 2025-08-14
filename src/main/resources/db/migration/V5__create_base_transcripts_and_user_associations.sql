-- V5: Create base transcripts and user associations tables for transcript deduplication
-- This migration creates the new schema to prevent duplicate video transcriptions

-- Create base_transcripts table to store core transcript data independent of users
CREATE TABLE base_transcripts (
    id UUID PRIMARY KEY,
    video_url TEXT NOT NULL UNIQUE, -- Unique constraint prevents duplicates
    transcript TEXT NOT NULL,
    description TEXT,
    title TEXT,
    duration DOUBLE PRECISION,
    uploaded_at TIMESTAMP,
    account_id VARCHAR(255),
    account VARCHAR(255),
    identifier_id VARCHAR(1024),
    identifier VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    transcribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance on base_transcripts
CREATE INDEX idx_base_transcripts_created_at ON base_transcripts(created_at);
CREATE INDEX idx_base_transcripts_transcribed_at ON base_transcripts(transcribed_at);

-- Create user_transcripts table to link users to base transcripts with user-specific data
CREATE TABLE user_transcripts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    base_transcript_id UUID NOT NULL,
    category_id UUID, -- User-specific categorization
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Ensure one association per user per transcript
    UNIQUE(user_id, base_transcript_id)
);

-- Add foreign key constraints for user_transcripts
ALTER TABLE user_transcripts 
    ADD CONSTRAINT fk_user_transcripts_user_id 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_transcripts 
    ADD CONSTRAINT fk_user_transcripts_base_transcript_id 
    FOREIGN KEY (base_transcript_id) REFERENCES base_transcripts(id) ON DELETE CASCADE;

ALTER TABLE user_transcripts 
    ADD CONSTRAINT fk_user_transcripts_category_id 
    FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE SET NULL;

-- Create indexes for performance on user_transcripts
CREATE INDEX idx_user_transcripts_user_id ON user_transcripts(user_id);
CREATE INDEX idx_user_transcripts_base_transcript_id ON user_transcripts(base_transcript_id);
CREATE INDEX idx_user_transcripts_category_id ON user_transcripts(category_id);
CREATE INDEX idx_user_transcripts_created_at ON user_transcripts(created_at);
CREATE INDEX idx_user_transcripts_last_accessed_at ON user_transcripts(last_accessed_at);

-- Create user_transcript_metadata table for storing user-specific metadata (optional future use)
CREATE TABLE user_transcript_metadata (
    id UUID PRIMARY KEY,
    user_transcript_id UUID NOT NULL,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(user_transcript_id, metadata_key)
);

-- Add foreign key constraint for user_transcript_metadata
ALTER TABLE user_transcript_metadata 
    ADD CONSTRAINT fk_user_transcript_metadata_user_transcript_id 
    FOREIGN KEY (user_transcript_id) REFERENCES user_transcripts(id) ON DELETE CASCADE;

-- Create index for performance on user_transcript_metadata
CREATE INDEX idx_user_transcript_metadata_user_transcript_id ON user_transcript_metadata(user_transcript_id);
CREATE INDEX idx_user_transcript_metadata_key ON user_transcript_metadata(metadata_key);

-- Add comments for documentation
COMMENT ON TABLE base_transcripts IS 'Stores core transcript data independent of users to prevent duplicate transcriptions';
COMMENT ON TABLE user_transcripts IS 'Links users to base transcripts with user-specific categorization and metadata';
COMMENT ON TABLE user_transcript_metadata IS 'Stores extensible user-specific metadata for transcripts';

COMMENT ON COLUMN user_transcripts.last_accessed_at IS 'Tracks when user last accessed this transcript for analytics';