package com.app.categorise.api.dto.subscription.apple;

/**
 * App Store Server Notification types.
 * See: https://developer.apple.com/documentation/appstoreservernotifications/notificationtype
 */
public enum AppStoreNotificationType {
    SUBSCRIBED,
    DID_RENEW,
    DID_CHANGE_RENEWAL_STATUS,
    EXPIRED,
    GRACE_PERIOD_EXPIRED,
    REFUND,
    REVOKE,
    DID_FAIL_TO_RENEW,
    CONSUMPTION_REQUEST,
    OFFER_REDEEMED,
    PRICE_INCREASE,
    RENEWAL_EXTENDED,
    RENEWAL_EXTENSION,
    TEST
}
