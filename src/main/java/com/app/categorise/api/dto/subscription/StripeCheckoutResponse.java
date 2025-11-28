package com.app.categorise.api.dto.subscription;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for Stripe checkout session creation
 */
public class StripeCheckoutResponse {

    @JsonProperty("checkoutUrl")
    private String checkoutUrl;

    @JsonProperty("sessionId")
    private String sessionId;

    public StripeCheckoutResponse() {}

    public StripeCheckoutResponse(String checkoutUrl, String sessionId) {
        this.checkoutUrl = checkoutUrl;
        this.sessionId = sessionId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}