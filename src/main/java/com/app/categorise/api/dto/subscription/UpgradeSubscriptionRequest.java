package com.app.categorise.api.dto.subscription;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for upgrading subscription
 */
public class UpgradeSubscriptionRequest {
    
    @NotBlank(message = "Purchase token is required")
    private String purchaseToken;
    
    @NotBlank(message = "Product ID is required")
    private String productId;
    
    public UpgradeSubscriptionRequest() {}
    
    public UpgradeSubscriptionRequest(String purchaseToken, String productId) {
        this.purchaseToken = purchaseToken;
        this.productId = productId;
    }
    
    public String getPurchaseToken() { return purchaseToken; }
    public void setPurchaseToken(String purchaseToken) { this.purchaseToken = purchaseToken; }
    
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
}