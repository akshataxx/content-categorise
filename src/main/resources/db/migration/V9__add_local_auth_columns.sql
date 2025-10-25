ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(255),
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Allow local users without OAuth sub
ALTER TABLE users ALTER COLUMN sub DROP NOT NULL;

-- Ensure username and email are unique independently
CREATE UNIQUE INDEX IF NOT EXISTS users_username_unique ON users(username) WHERE username IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON users(email) WHERE email IS NOT NULL;
