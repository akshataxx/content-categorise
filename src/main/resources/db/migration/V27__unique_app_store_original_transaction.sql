-- Prevent one App Store subscription from being attached to multiple users.
DROP INDEX IF EXISTS idx_user_subscriptions_apple_orig_tx;

CREATE UNIQUE INDEX uq_user_subscriptions_apple_orig_tx
ON user_subscriptions(apple_original_transaction_id)
WHERE apple_original_transaction_id IS NOT NULL;
