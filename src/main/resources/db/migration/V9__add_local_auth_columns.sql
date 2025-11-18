ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Allow local users without OAuth sub
ALTER TABLE users ALTER COLUMN sub DROP NOT NULL;

-- Ensure email is unique when present
CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON users(email) WHERE email IS NOT NULL;
