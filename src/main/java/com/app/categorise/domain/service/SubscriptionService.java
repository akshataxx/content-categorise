package com.app.categorise.domain.service;

import com.app.categorise.domain.model.UserSubscription;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain service interface for subscription management
 */
public interface SubscriptionService {

    /**
     * Get user's current subscription
     */
    Optional<UserSubscription> getUserSubscription(UUID userId);

    /**
     * Check if user has premium subscription
     */
    boolean hasActivePremiumSubscription(UUID userId);

    /**
     * Initialize free subscription for new user
     */
    void initializeFreeSubscription(UUID userId);

    /**
     * Upgrade user to premium subscription with Google Play details
     */
    UserSubscription upgradeToPremiumWithGooglePlay(UUID userId, String purchaseToken,
                                                    String productId, String orderId,
                                                    UserSubscription.SubscriptionType type);

    /**
     * Cancel user's subscription
     */
    void cancelSubscription(UUID userId);

    /**
     * Get remaining free transcriptions for user
     */
    int getRemainingFreeTranscriptions(UUID userId);
}