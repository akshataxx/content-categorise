-- V8: Populate default rate limits for existing users
-- This migration ensures all existing users have default rate limit configurations

-- Insert default rate limits for all existing users who don't have limits yet
INSERT INTO user_rate_limits (id, user_id, transcripts_per_minute_limit, transcripts_per_day_limit, total_transcripts_limit, created_at, updated_at)
SELECT 
    gen_random_uuid() as id,
    u.id as user_id,
    5 as transcripts_per_minute_limit,    -- Default: 5 transcripts per minute
    100 as transcripts_per_day_limit,     -- Default: 100 transcripts per day  
    3 as total_transcripts_limit,     -- Default: 3 total transcripts (free tier)
    CURRENT_TIMESTAMP as created_at,
    CURRENT_TIMESTAMP as updated_at
FROM users u
WHERE u.id NOT IN (
    SELECT user_id FROM user_rate_limits WHERE user_id IS NOT NULL
);
