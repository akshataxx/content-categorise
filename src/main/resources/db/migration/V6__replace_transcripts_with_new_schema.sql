-- V5: Replace old transcripts table with new deduplication schema
-- Since this is non-production, we can safely drop the old table and start fresh

-- Drop old transcripts table (no production data to preserve)
DROP TABLE IF EXISTS transcripts CASCADE;

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

-- Create user_transcripts table to link users to base transcripts with user-specific data
CREATE TABLE user_transcripts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    base_transcript_id UUID NOT NULL,
    category_id UUID,
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

-- Add comments for documentation
COMMENT ON TABLE base_transcripts IS 'Stores core transcript data independent of users to prevent duplicate transcriptions';
COMMENT ON TABLE user_transcripts IS 'Links users to base transcripts with user-specific categorization and metadata';
