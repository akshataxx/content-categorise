-- V16__create_processed_notifications_table.sql
-- Create table for idempotent webhook notification processing

CREATE TABLE processed_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(255) NOT NULL UNIQUE,
    notification_type VARCHAR(100),
    source VARCHAR(20) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for fast lookups by notification_id (unique constraint already creates one,
-- but being explicit for clarity)
CREATE INDEX idx_processed_notifications_source
ON processed_notifications(source);
