-- V15__add_app_store_subscription_fields.sql
-- Add Apple App Store subscription support

-- Add Apple-specific columns
ALTER TABLE user_subscriptions
ADD COLUMN apple_original_transaction_id VARCHAR(100);

ALTER TABLE user_subscriptions
ADD COLUMN apple_transaction_id VARCHAR(100);

ALTER TABLE user_subscriptions
ADD COLUMN apple_product_id VARCHAR(100);

-- Add subscription source to track provider
ALTER TABLE user_subscriptions
ADD COLUMN subscription_source VARCHAR(20) DEFAULT 'GOOGLE_PLAY';

-- Index for webhook lookups by original transaction ID
CREATE INDEX idx_user_subscriptions_apple_orig_tx
ON user_subscriptions(apple_original_transaction_id);

-- Backfill existing subscriptions as Google Play
UPDATE user_subscriptions
SET subscription_source = 'GOOGLE_PLAY'
WHERE google_play_purchase_token IS NOT NULL
  AND subscription_source IS NULL;
