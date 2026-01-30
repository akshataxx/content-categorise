-- V12: Replace Stripe billing columns with Google Play Billing columns

-- Remove Stripe-specific columns
ALTER TABLE user_subscriptions DROP COLUMN IF EXISTS stripe_customer_id;
ALTER TABLE user_subscriptions DROP COLUMN IF EXISTS stripe_subscription_id;
ALTER TABLE user_subscriptions DROP COLUMN IF EXISTS stripe_price_id;

-- Add Google Play Billing columns
ALTER TABLE user_subscriptions ADD COLUMN google_play_purchase_token VARCHAR(500);
ALTER TABLE user_subscriptions ADD COLUMN google_play_product_id VARCHAR(100);
ALTER TABLE user_subscriptions ADD COLUMN google_play_order_id VARCHAR(100);

-- Create index for efficient purchase token lookup
CREATE INDEX idx_user_subscriptions_google_play_token ON user_subscriptions(google_play_purchase_token);
