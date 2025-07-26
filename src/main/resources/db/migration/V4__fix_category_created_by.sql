ALTER TABLE category
ALTER COLUMN created_by TYPE UUID USING created_by::uuid;