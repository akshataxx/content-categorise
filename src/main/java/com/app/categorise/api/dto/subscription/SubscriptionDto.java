package com.app.categorise.api.dto.subscription;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for subscription information
 */
public class SubscriptionDto {
    private UUID id;
    private UUID userId;
    private String subscriptionType;
    private String status;
    private Instant subscriptionStartDate;
    private Instant subscriptionEndDate;
    private boolean autoRenew;
    private boolean isPremium;
    
    public SubscriptionDto() {}
    
    public SubscriptionDto(UUID id, UUID userId, String subscriptionType, String status,
                          Instant subscriptionStartDate, Instant subscriptionEndDate,
                          boolean autoRenew, boolean isPremium) {
        this.id = id;
        this.userId = userId;
        this.subscriptionType = subscriptionType;
        this.status = status;
        this.subscriptionStartDate = subscriptionStartDate;
        this.subscriptionEndDate = subscriptionEndDate;
        this.autoRenew = autoRenew;
        this.isPremium = isPremium;
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    
    public String getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Instant getSubscriptionStartDate() { return subscriptionStartDate; }
    public void setSubscriptionStartDate(Instant subscriptionStartDate) { this.subscriptionStartDate = subscriptionStartDate; }
    
    public Instant getSubscriptionEndDate() { return subscriptionEndDate; }
    public void setSubscriptionEndDate(Instant subscriptionEndDate) { this.subscriptionEndDate = subscriptionEndDate; }
    
    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }
    
    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }
}