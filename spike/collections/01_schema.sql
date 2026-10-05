-- Spike only: candidate V28 for option A (sparse key) plus a dense table for option B comparison.
CREATE TABLE collections (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  name_key VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  CONSTRAINT uq_collections_user_name_key UNIQUE (user_id, name_key),
  CONSTRAINT ck_collections_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE TABLE collection_items (
  collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
  user_transcript_id UUID NOT NULL REFERENCES user_transcripts(id) ON DELETE CASCADE,
  sort_key BIGINT NOT NULL,
  added_at TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (collection_id, user_transcript_id)
);
CREATE INDEX idx_ci_order ON collection_items (collection_id, sort_key, user_transcript_id);
CREATE INDEX idx_ci_ut ON collection_items (user_transcript_id);
-- Option B: dense positions, full-list rewrite
CREATE TABLE collection_items_b (
  collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
  user_transcript_id UUID NOT NULL REFERENCES user_transcripts(id) ON DELETE CASCADE,
  position INT NOT NULL,
  PRIMARY KEY (collection_id, user_transcript_id)
);
CREATE INDEX idx_cib_order ON collection_items_b (collection_id, position);
