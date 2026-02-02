package com.app.categorise.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing a user's subscription
 */
public class UserSubscription {

    public enum SubscriptionType {
        FREE,
        PREMIUM_MONTHLY,
        PREMIUM_YEARLY
    }

    public enum SubscriptionStatus {
        ACTIVE,
        CANCELLED,
        EXPIRED,
        PENDING
    }

    private UUID id;
    private UUID userId;
    private SubscriptionType subscriptionType;
    private SubscriptionStatus status;
    private String googlePlayPurchaseToken;
    private String googlePlayProductId;
    private String googlePlayOrderId;
    private Instant subscriptionStartDate;
    private Instant subscriptionEndDate;
    private boolean autoRenew;
    private Instant createdAt;
    private Instant updatedAt;

    public UserSubscription() {}

    public UserSubscription(UUID id, UUID userId, SubscriptionType subscriptionType,
                       SubscriptionStatus status, String googlePlayPurchaseToken,
                       String googlePlayProductId, String googlePlayOrderId,
                       Instant subscriptionStartDate, Instant subscriptionEndDate,
                       boolean autoRenew, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.subscriptionType = subscriptionType;
        this.status = status;
        this.googlePlayPurchaseToken = googlePlayPurchaseToken;
        this.googlePlayProductId = googlePlayProductId;
        this.googlePlayOrderId = googlePlayOrderId;
        this.subscriptionStartDate = subscriptionStartDate;
        this.subscriptionEndDate = subscriptionEndDate;
        this.autoRenew = autoRenew;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public SubscriptionType getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(SubscriptionType subscriptionType) { this.subscriptionType = subscriptionType; }

    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }

    public String getGooglePlayPurchaseToken() { return googlePlayPurchaseToken; }
    public void setGooglePlayPurchaseToken(String googlePlayPurchaseToken) { this.googlePlayPurchaseToken = googlePlayPurchaseToken; }

    public String getGooglePlayProductId() { return googlePlayProductId; }
    public void setGooglePlayProductId(String googlePlayProductId) { this.googlePlayProductId = googlePlayProductId; }

    public String getGooglePlayOrderId() { return googlePlayOrderId; }
    public void setGooglePlayOrderId(String googlePlayOrderId) { this.googlePlayOrderId = googlePlayOrderId; }

    public Instant getSubscriptionStartDate() { return subscriptionStartDate; }
    public void setSubscriptionStartDate(Instant subscriptionStartDate) { this.subscriptionStartDate = subscriptionStartDate; }

    public Instant getSubscriptionEndDate() { return subscriptionEndDate; }
    public void setSubscriptionEndDate(Instant subscriptionEndDate) { this.subscriptionEndDate = subscriptionEndDate; }

    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Check if this subscription provides premium access
     */
    public boolean isPremium() {
        return (subscriptionType == SubscriptionType.PREMIUM_MONTHLY ||
                subscriptionType == SubscriptionType.PREMIUM_YEARLY) &&
               status == SubscriptionStatus.ACTIVE;
    }

    /**
     * Check if subscription is currently active
     */
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE &&
               (subscriptionEndDate == null || subscriptionEndDate.isAfter(Instant.now()));
    }
}
