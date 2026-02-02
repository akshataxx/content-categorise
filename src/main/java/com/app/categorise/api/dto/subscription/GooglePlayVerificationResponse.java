package com.app.categorise.api.dto.subscription;

import java.time.Instant;

/**
 * Response DTO for Google Play purchase verification
 */
public class GooglePlayVerificationResponse {

    private boolean verified;
    private boolean subscriptionActive;
    private Instant expirationTime;
    private String errorMessage;

    public GooglePlayVerificationResponse() {
    }

    public GooglePlayVerificationResponse(boolean verified, boolean subscriptionActive,
                                          Instant expirationTime, String errorMessage) {
        this.verified = verified;
        this.subscriptionActive = subscriptionActive;
        this.expirationTime = expirationTime;
        this.errorMessage = errorMessage;
    }

    public static GooglePlayVerificationResponse success(boolean subscriptionActive, Instant expirationTime) {
        return new GooglePlayVerificationResponse(true, subscriptionActive, expirationTime, null);
    }

    public static GooglePlayVerificationResponse failure(String errorMessage) {
        return new GooglePlayVerificationResponse(false, false, null, errorMessage);
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public boolean isSubscriptionActive() {
        return subscriptionActive;
    }

    public void setSubscriptionActive(boolean subscriptionActive) {
        this.subscriptionActive = subscriptionActive;
    }

    public Instant getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Instant expirationTime) {
        this.expirationTime = expirationTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
