package com.app.categorise.api.dto.subscription;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a Stripe checkout session
 */
public class StripeCheckoutRequest {

    @NotBlank(message = "Price ID is required")
    @JsonProperty("priceId")
    private String priceId;

    public StripeCheckoutRequest() {}

    public StripeCheckoutRequest(String priceId) {
        this.priceId = priceId;
    }

    public String getPriceId() {
        return priceId;
    }

    public void setPriceId(String priceId) {
        this.priceId = priceId;
    }
}