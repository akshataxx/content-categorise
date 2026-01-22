-- Migration V12: Add Apple Sign-In support and clean up auth constraints

-- Add appleUserId column to users table
ALTER TABLE users ADD COLUMN apple_user_id VARCHAR(255);

-- Add unique constraint on apple_user_id (only when not null)
CREATE UNIQUE INDEX idx_users_apple_user_id ON users(apple_user_id) WHERE apple_user_id IS NOT NULL;

-- Remove the legacy (sub, email) composite constraint - doesn't serve a purpose
-- with multiple auth providers. Email uniqueness is already enforced by users_email_unique.
DROP INDEX IF EXISTS users_sub_email_unique;

-- Remove the unique constraint on sub column - Apple users don't have a sub,
-- and Google's sub is still looked up but doesn't need to be globally unique.
DROP INDEX IF EXISTS users_sub_key;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_sub_key;
