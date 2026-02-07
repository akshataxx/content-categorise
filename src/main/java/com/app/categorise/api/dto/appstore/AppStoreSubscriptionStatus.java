package com.app.categorise.api.dto.appstore;

import java.time.Instant;

/**
 * Represents the current subscription status from Apple's App Store Server API.
 * Used when querying subscription status for a given original transaction ID.
 */
public class AppStoreSubscriptionStatus {

    private String originalTransactionId;
    private String productId;
    private Instant expiresDate;
    private boolean autoRenewEnabled;
    private String status; // ACTIVE, EXPIRED, BILLING_RETRY, etc.

    public AppStoreSubscriptionStatus() {}

    private AppStoreSubscriptionStatus(Builder builder) {
        this.originalTransactionId = builder.originalTransactionId;
        this.productId = builder.productId;
        this.expiresDate = builder.expiresDate;
        this.autoRenewEnabled = builder.autoRenewEnabled;
        this.status = builder.status;
    }

    public static Builder builder() { return new Builder(); }

    public String getOriginalTransactionId() { return originalTransactionId; }
    public String getProductId() { return productId; }
    public Instant getExpiresDate() { return expiresDate; }
    public boolean isAutoRenewEnabled() { return autoRenewEnabled; }
    public String getStatus() { return status; }

    public static class Builder {
        private String originalTransactionId;
        private String productId;
        private Instant expiresDate;
        private boolean autoRenewEnabled;
        private String status;

        public Builder originalTransactionId(String originalTransactionId) { this.originalTransactionId = originalTransactionId; return this; }
        public Builder productId(String productId) { this.productId = productId; return this; }
        public Builder expiresDate(Instant expiresDate) { this.expiresDate = expiresDate; return this; }
        public Builder autoRenewEnabled(boolean autoRenewEnabled) { this.autoRenewEnabled = autoRenewEnabled; return this; }
        public Builder status(String status) { this.status = status; return this; }

        public AppStoreSubscriptionStatus build() { return new AppStoreSubscriptionStatus(this); }
    }
}
