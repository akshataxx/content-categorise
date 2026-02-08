package com.app.categorise.domain.model;

/**
 * Tracks which platform a subscription was purchased through.
 * Used to route verification and handle cross-platform scenarios.
 */
public enum SubscriptionSource {
    /**
     * Subscription purchased through Google Play Store (Android)
     */
    GOOGLE_PLAY,

    /**
     * Subscription purchased through Apple App Store (iOS)
     */
    APP_STORE
}
