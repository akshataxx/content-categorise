-- Create category table
CREATE TABLE category (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    created_by VARCHAR(255)
);
-- Add index explicitly for name (optional but fine since UNIQUE also implies one)
CREATE INDEX idx_category_name ON category(name);

-- Create category_aliases table
CREATE TABLE category_aliases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category_id UUID NOT NULL,
    alias VARCHAR(255) NOT NULL
);

-- Create transcripts table
CREATE TABLE transcripts (
    id UUID PRIMARY KEY,
    video_url TEXT NOT NULL,
    transcript TEXT NOT NULL,
    description TEXT,
    title TEXT,
    duration DOUBLE PRECISION,
    uploaded_at TIMESTAMP,
    account_id VARCHAR(255),
    account VARCHAR(255),
    identifier_id VARCHAR(1024),
    identifier VARCHAR(255),
    category_id UUID,
    user_id UUID,
    created_at TIMESTAMP
);
