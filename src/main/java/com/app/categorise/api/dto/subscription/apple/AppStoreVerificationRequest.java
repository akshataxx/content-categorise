package com.app.categorise.api.dto.subscription.apple;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for verifying an App Store transaction.
 * Sent from the iOS app after a StoreKit 2 purchase.
 */
public class AppStoreVerificationRequest {

    @NotBlank(message = "signedTransaction is required")
    private String signedTransaction;

    @NotBlank(message = "productId is required")
    private String productId;

    @NotBlank(message = "originalTransactionId is required")
    private String originalTransactionId;

    public AppStoreVerificationRequest() {}

    public AppStoreVerificationRequest(String signedTransaction, String productId, String originalTransactionId) {
        this.signedTransaction = signedTransaction;
        this.productId = productId;
        this.originalTransactionId = originalTransactionId;
    }

    public String getSignedTransaction() { return signedTransaction; }
    public void setSignedTransaction(String signedTransaction) { this.signedTransaction = signedTransaction; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getOriginalTransactionId() { return originalTransactionId; }
    public void setOriginalTransactionId(String originalTransactionId) { this.originalTransactionId = originalTransactionId; }
}
