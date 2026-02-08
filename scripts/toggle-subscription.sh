#!/usr/bin/env bash
#
# Toggle a user's subscription between FREE and PREMIUM in the local Docker database.
# Usage: ./scripts/toggle-subscription.sh
#

set -euo pipefail

DB="contentdb"
DB_USER="postgres"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

# Rate limit defaults
FREE_PER_MIN=5
FREE_PER_DAY=100
FREE_TOTAL=3

PREMIUM_PER_MIN=5
PREMIUM_PER_DAY=100
PREMIUM_TOTAL=10000

run_sql() {
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB" -tAc "$1"
}

# ── List users ──────────────────────────────────────────────────────────────
echo ""
echo "📋  Users in the database:"
echo "─────────────────────────────────────────────────────────────────"
printf "%-40s %-30s %-20s\n" "ID" "EMAIL" "SUBSCRIPTION"

run_sql "
  SELECT u.id || '|' || COALESCE(u.email, u.sub, u.apple_user_id, 'unknown') || '|' || COALESCE(s.subscription_type, 'NO SUB')
  FROM users u
  LEFT JOIN user_subscriptions s ON s.user_id = u.id
  ORDER BY u.email;
" | while IFS='|' read -r uid uemail usub; do
  printf "%-40s %-30s %-20s\n" "$uid" "$uemail" "$usub"
done

echo "─────────────────────────────────────────────────────────────────"
echo ""

# ── Pick a user ─────────────────────────────────────────────────────────────
read -rp "Enter user email (or google_id): " USER_INPUT

USER_ID=$(run_sql "SELECT id FROM users WHERE email = '$USER_INPUT' OR sub = '$USER_INPUT' OR apple_user_id = '$USER_INPUT' LIMIT 1;")

if [[ -z "$USER_ID" ]]; then
  echo "❌  No user found matching '$USER_INPUT'"
  exit 1
fi

# ── Show current subscription ───────────────────────────────────────────────
CURRENT_TYPE=$(run_sql "SELECT subscription_type FROM user_subscriptions WHERE user_id = '$USER_ID';")
CURRENT_STATUS=$(run_sql "SELECT status FROM user_subscriptions WHERE user_id = '$USER_ID';")
CURRENT_TOTAL_LIMIT=$(run_sql "SELECT total_transcripts_limit FROM user_rate_limits WHERE user_id = '$USER_ID';")

echo ""
echo "👤  User: $USER_INPUT ($USER_ID)"
echo "    Subscription: ${CURRENT_TYPE:-NONE} (${CURRENT_STATUS:-N/A})"
echo "    Total transcript limit: ${CURRENT_TOTAL_LIMIT:-N/A}"
echo ""

# ── Determine direction ─────────────────────────────────────────────────────
if [[ "$CURRENT_TYPE" == "FREE" || -z "$CURRENT_TYPE" ]]; then
  TARGET="PREMIUM_MONTHLY"
  TARGET_LABEL="PREMIUM"
  TOTAL_LIMIT=$PREMIUM_TOTAL
  PER_MIN=$PREMIUM_PER_MIN
  PER_DAY=$PREMIUM_PER_DAY
else
  TARGET="FREE"
  TARGET_LABEL="FREE"
  TOTAL_LIMIT=$FREE_TOTAL
  PER_MIN=$FREE_PER_MIN
  PER_DAY=$FREE_PER_DAY
fi

echo "🔄  Will switch from ${CURRENT_TYPE:-NONE} → $TARGET_LABEL"
echo "    New rate limits: $PER_MIN/min, $PER_DAY/day, $TOTAL_LIMIT total"
echo ""
read -rp "Proceed? (y/N): " CONFIRM

if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
  echo "Aborted."
  exit 0
fi

# ── Apply changes ───────────────────────────────────────────────────────────
if [[ "$TARGET" == "FREE" ]]; then
  # Downgrade to free
  run_sql "
    UPDATE user_subscriptions
    SET subscription_type = 'FREE',
        status = 'ACTIVE',
        google_play_purchase_token = NULL,
        google_play_product_id = NULL,
        google_play_order_id = NULL,
        subscription_end_date = NULL,
        auto_renew = false,
        updated_at = NOW()
    WHERE user_id = '$USER_ID';
  "
else
  # Upgrade to premium
  EXISTING=$(run_sql "SELECT COUNT(*) FROM user_subscriptions WHERE user_id = '$USER_ID';")
  if [[ "$EXISTING" -eq 0 ]]; then
    run_sql "
      INSERT INTO user_subscriptions (user_id, subscription_type, status, auto_renew, subscription_start_date, created_at, updated_at)
      VALUES ('$USER_ID', '$TARGET', 'ACTIVE', true, NOW(), NOW(), NOW());
    "
  else
    run_sql "
      UPDATE user_subscriptions
      SET subscription_type = '$TARGET',
          status = 'ACTIVE',
          auto_renew = true,
          subscription_start_date = NOW(),
          subscription_end_date = NULL,
          updated_at = NOW()
      WHERE user_id = '$USER_ID';
    "
  fi
fi

# Update rate limits
EXISTING_LIMIT=$(run_sql "SELECT COUNT(*) FROM user_rate_limits WHERE user_id = '$USER_ID';")
if [[ "$EXISTING_LIMIT" -eq 0 ]]; then
  run_sql "
    INSERT INTO user_rate_limits (id, user_id, transcripts_per_minute_limit, transcripts_per_day_limit, total_transcripts_limit, created_at, updated_at)
    VALUES (gen_random_uuid(), '$USER_ID', $PER_MIN, $PER_DAY, $TOTAL_LIMIT, NOW(), NOW());
  "
else
  run_sql "
    UPDATE user_rate_limits
    SET transcripts_per_minute_limit = $PER_MIN,
        transcripts_per_day_limit = $PER_DAY,
        total_transcripts_limit = $TOTAL_LIMIT,
        updated_at = NOW()
    WHERE user_id = '$USER_ID';
  "
fi

# ── Verify ──────────────────────────────────────────────────────────────────
NEW_TYPE=$(run_sql "SELECT subscription_type FROM user_subscriptions WHERE user_id = '$USER_ID';")
NEW_LIMIT=$(run_sql "SELECT total_transcripts_limit FROM user_rate_limits WHERE user_id = '$USER_ID';")

echo ""
echo "✅  Done!"
echo "    Subscription: $NEW_TYPE"
echo "    Total transcript limit: $NEW_LIMIT"
