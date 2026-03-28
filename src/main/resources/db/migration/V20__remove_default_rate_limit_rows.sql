-- Remove all existing rate limit config rows.
-- These were tier-default rows seeded on every user registration.
-- Going forward, tier defaults are derived from subscription status at runtime.
-- Only per-user override rows (manually set for special cases) should exist in this table.
DELETE FROM user_rate_limits;
