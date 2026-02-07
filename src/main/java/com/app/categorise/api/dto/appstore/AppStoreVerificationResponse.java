package com.app.categorise.api.dto.appstore;

import java.time.Instant;

/**
 * Response DTO for App Store transaction verification.
 * Returned to the iOS app after verifying a purchase.
 */
public class AppStoreVerificationResponse {

    private boolean verified;
    private boolean subscriptionActive;
    private Instant expirationTime;
    private String errorMessage;

    public AppStoreVerificationResponse() {}

    public AppStoreVerificationResponse(boolean verified, boolean subscriptionActive,
                                        Instant expirationTime, String errorMessage) {
        this.verified = verified;
        this.subscriptionActive = subscriptionActive;
        this.expirationTime = expirationTime;
        this.errorMessage = errorMessage;
    }

    public static AppStoreVerificationResponse success(boolean subscriptionActive, Instant expirationTime) {
        return new AppStoreVerificationResponse(true, subscriptionActive, expirationTime, null);
    }

    public static AppStoreVerificationResponse failure(String errorMessage) {
        return new AppStoreVerificationResponse(false, false, null, errorMessage);
    }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public boolean isSubscriptionActive() { return subscriptionActive; }
    public void setSubscriptionActive(boolean subscriptionActive) { this.subscriptionActive = subscriptionActive; }

    public Instant getExpirationTime() { return expirationTime; }
    public void setExpirationTime(Instant expirationTime) { this.expirationTime = expirationTime; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
