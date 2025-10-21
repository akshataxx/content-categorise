-- V9: Add subscription tables for user billing management

-- User subscription status and plan information
CREATE TABLE user_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_type VARCHAR(20) NOT NULL CHECK (subscription_type IN ('FREE', 'PREMIUM_MONTHLY', 'PREMIUM_YEARLY')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED', 'PENDING')),
    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    stripe_price_id VARCHAR(100),
    subscription_start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subscription_end_date TIMESTAMP,
    auto_renew BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_user_subscription UNIQUE(user_id)
);

-- Create indexes for efficient querying
CREATE INDEX idx_user_subscriptions_user_id ON user_subscriptions(user_id);
CREATE INDEX idx_user_subscriptions_status ON user_subscriptions(status);
CREATE INDEX idx_user_subscriptions_type ON user_subscriptions(subscription_type);

-- Initialize all existing users with FREE subscription
INSERT INTO user_subscriptions (user_id, subscription_type, status)
SELECT id, 'FREE', 'ACTIVE' 
FROM users 
WHERE id NOT IN (SELECT user_id FROM user_subscriptions WHERE user_id IS NOT NULL);

-- Update rate limits for premium users (will be managed via application logic)
-- Premium users get higher limits: 1000 per day, 100,000 total