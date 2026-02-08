package com.app.categorise.api.dto.subscription.apple;

import java.time.Instant;

/**
 * Represents a decoded JWS transaction from Apple.
 * Contains the extracted fields from a StoreKit 2 signed transaction payload.
 */
public class DecodedTransaction {

    private String transactionId;
    private String originalTransactionId;
    private String productId;
    private Instant purchaseDate;
    private Instant expiresDate;
    private String environment;

    public DecodedTransaction() {}

    private DecodedTransaction(Builder builder) {
        this.transactionId = builder.transactionId;
        this.originalTransactionId = builder.originalTransactionId;
        this.productId = builder.productId;
        this.purchaseDate = builder.purchaseDate;
        this.expiresDate = builder.expiresDate;
        this.environment = builder.environment;
    }

    public static Builder builder() { return new Builder(); }

    public String getTransactionId() { return transactionId; }
    public String getOriginalTransactionId() { return originalTransactionId; }
    public String getProductId() { return productId; }
    public Instant getPurchaseDate() { return purchaseDate; }
    public Instant getExpiresDate() { return expiresDate; }
    public String getEnvironment() { return environment; }

    public static class Builder {
        private String transactionId;
        private String originalTransactionId;
        private String productId;
        private Instant purchaseDate;
        private Instant expiresDate;
        private String environment;

        public Builder transactionId(String transactionId) { this.transactionId = transactionId; return this; }
        public Builder originalTransactionId(String originalTransactionId) { this.originalTransactionId = originalTransactionId; return this; }
        public Builder productId(String productId) { this.productId = productId; return this; }
        public Builder purchaseDate(Instant purchaseDate) { this.purchaseDate = purchaseDate; return this; }
        public Builder expiresDate(Instant expiresDate) { this.expiresDate = expiresDate; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }

        public DecodedTransaction build() { return new DecodedTransaction(this); }
    }
}
