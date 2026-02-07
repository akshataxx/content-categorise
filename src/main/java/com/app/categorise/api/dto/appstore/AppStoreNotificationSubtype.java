package com.app.categorise.api.dto.appstore;

/**
 * App Store Server Notification subtypes.
 * See: https://developer.apple.com/documentation/appstoreservernotifications/subtype
 */
public enum AppStoreNotificationSubtype {
    INITIAL_BUY,
    RESUBSCRIBE,
    DOWNGRADE,
    UPGRADE,
    AUTO_RENEW_ENABLED,
    AUTO_RENEW_DISABLED,
    VOLUNTARY,
    BILLING_RETRY,
    PRICE_INCREASE,
    GRACE_PERIOD,
    PENDING,
    ACCEPTED,
    BILLING_RECOVERY,
    PRODUCT_NOT_FOR_SALE,
    SUMMARY,
    FAILURE
}
