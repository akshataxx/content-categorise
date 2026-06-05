CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE base_transcripts ADD COLUMN IF NOT EXISTS embedding vector(1536);

CREATE INDEX IF NOT EXISTS idx_base_transcripts_embedding
    ON base_transcripts USING hnsw (embedding vector_cosine_ops);
