CREATE TABLE user_subcategory (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id UUID NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_subcategory_user_parent_name
        UNIQUE (user_id, parent_id, name)
);

CREATE INDEX idx_user_subcategory_user_id ON user_subcategory(user_id);
CREATE INDEX idx_user_subcategory_parent_id ON user_subcategory(parent_id);

ALTER TABLE user_transcripts
    ADD COLUMN user_subcategory_id UUID NULL
        REFERENCES user_subcategory(id) ON DELETE SET NULL;

CREATE INDEX idx_user_transcripts_user_subcategory_id
    ON user_transcripts(user_subcategory_id);
