package com.app.categorise.domain.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;

import java.util.UUID;

/**
 * Domain service interface for payment operations
 * Abstracts payment gateway operations to allow for multiple implementations
 */
public interface PaymentService {

    /**
     * Create a checkout session for subscription purchase
     *
     * @param userId the user making the purchase
     * @param priceId the price ID from the payment gateway
     * @return the checkout session
     * @throws StripeException if checkout session creation fails
     */
    Session createCheckoutSession(UUID userId, String priceId) throws StripeException;

    /**
     * Retrieve a checkout session by ID
     *
     * @param sessionId the session ID to retrieve
     * @return the checkout session
     * @throws StripeException if session retrieval fails
     */
    Session retrieveSession(String sessionId) throws StripeException;
}
