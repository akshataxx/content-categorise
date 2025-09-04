package com.app.categorise.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for user subscriptions
 */
@Entity
@Table(name = "user_subscriptions")
public class UserSubscriptionEntity {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_type", nullable = false)
    private SubscriptionType subscriptionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;
    
    @Column(name = "google_play_purchase_token")
    private String googlePlayPurchaseToken;
    
    @Column(name = "google_play_product_id")
    private String googlePlayProductId;
    
    @Column(name = "subscription_start_date", nullable = false)
    private Instant subscriptionStartDate;
    
    @Column(name = "subscription_end_date")
    private Instant subscriptionEndDate;
    
    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
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
    
    public UserSubscriptionEntity() {}
    
    public UserSubscriptionEntity(UUID userId, SubscriptionType subscriptionType, SubscriptionStatus status) {
        this.userId = userId;
        this.subscriptionType = subscriptionType;
        this.status = status;
        this.subscriptionStartDate = Instant.now();
        this.autoRenew = true;
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
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
}