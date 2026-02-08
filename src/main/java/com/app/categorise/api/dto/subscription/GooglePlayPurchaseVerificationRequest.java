package com.app.categorise.api.dto.subscription;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for verifying a Google Play purchase.
 * TODO: Move Google Play DTOs to api/dto/subscription/googleplay/ sub-package for consistency with apple/.
 */
public class GooglePlayPurchaseVerificationRequest {

    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "Purchase token is required")
    private String purchaseToken;

    private String orderId;

    public GooglePlayPurchaseVerificationRequest() {
    }

    public GooglePlayPurchaseVerificationRequest(String productId, String purchaseToken, String orderId) {
        this.productId = productId;
        this.purchaseToken = purchaseToken;
        this.orderId = orderId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getPurchaseToken() {
        return purchaseToken;
    }

    public void setPurchaseToken(String purchaseToken) {
        this.purchaseToken = purchaseToken;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
