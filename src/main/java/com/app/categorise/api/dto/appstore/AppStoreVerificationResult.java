package com.app.categorise.api.dto.appstore;

import java.time.Instant;

/**
 * Internal result object for App Store transaction verification.
 * Used by the billing service to pass verification results to the subscription service.
 */
public class AppStoreVerificationResult {

    private boolean verified;
    private boolean subscriptionActive;
    private Instant expirationTime;
    private String originalTransactionId;
    private String transactionId;
    private String productId;
    private String errorMessage;

    public AppStoreVerificationResult() {}

    private AppStoreVerificationResult(Builder builder) {
        this.verified = builder.verified;
        this.subscriptionActive = builder.subscriptionActive;
        this.expirationTime = builder.expirationTime;
        this.originalTransactionId = builder.originalTransactionId;
        this.transactionId = builder.transactionId;
        this.productId = builder.productId;
        this.errorMessage = builder.errorMessage;
    }

    public static Builder builder() { return new Builder(); }

    public boolean isVerified() { return verified; }
    public boolean isSubscriptionActive() { return subscriptionActive; }
    public Instant getExpirationTime() { return expirationTime; }
    public String getOriginalTransactionId() { return originalTransactionId; }
    public String getTransactionId() { return transactionId; }
    public String getProductId() { return productId; }
    public String getErrorMessage() { return errorMessage; }

    public static class Builder {
        private boolean verified;
        private boolean subscriptionActive;
        private Instant expirationTime;
        private String originalTransactionId;
        private String transactionId;
        private String productId;
        private String errorMessage;

        public Builder verified(boolean verified) { this.verified = verified; return this; }
        public Builder subscriptionActive(boolean subscriptionActive) { this.subscriptionActive = subscriptionActive; return this; }
        public Builder expirationTime(Instant expirationTime) { this.expirationTime = expirationTime; return this; }
        public Builder originalTransactionId(String originalTransactionId) { this.originalTransactionId = originalTransactionId; return this; }
        public Builder transactionId(String transactionId) { this.transactionId = transactionId; return this; }
        public Builder productId(String productId) { this.productId = productId; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public AppStoreVerificationResult build() { return new AppStoreVerificationResult(this); }
    }
}
