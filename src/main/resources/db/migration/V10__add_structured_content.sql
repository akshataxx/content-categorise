-- V10: Add structured content column for formatted transcript display

-- Add structured_content column to base_transcripts
-- This will store JSON data with extracted information like ingredients, steps, products, etc.
ALTER TABLE base_transcripts
ADD COLUMN structured_content JSONB;

-- Add index for JSONB queries (optional but recommended for performance)
CREATE INDEX idx_base_transcripts_structured_content ON base_transcripts USING GIN (structured_content);

-- Add comment explaining the column
COMMENT ON COLUMN base_transcripts.structured_content IS 'Structured JSON content extracted from transcript (ingredients, steps, products, key points, etc.)';
