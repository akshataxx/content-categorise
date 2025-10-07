-- V10: Add Stripe-specific fields to user_subscriptions

-- Add Stripe customer and subscription IDs
ALTER TABLE user_subscriptions
ADD COLUMN stripe_customer_id VARCHAR(255),
ADD COLUMN stripe_subscription_id VARCHAR(255);

-- Create indexes for efficient Stripe lookups
CREATE INDEX idx_stripe_customer_id ON user_subscriptions(stripe_customer_id);
CREATE INDEX idx_stripe_subscription_id ON user_subscriptions(stripe_subscription_id);
