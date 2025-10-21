package com.app.categorise.domain.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.UUID;

/**
 * Stripe implementation of PaymentService
 * Handles Stripe-specific payment operations
 */
@Service
public class StripeService implements PaymentService {

    @Value("${stripe.secret.key:sk_test_}")
    private String stripeSecretKey;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @Override
    public Session createCheckoutSession(UUID userId, String priceId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl("transcribeassistant://subscription/success?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl("transcribeassistant://subscription/cancel")
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )
            .setClientReferenceId(userId.toString())
            .setCustomerEmail(null) // Let user enter email during checkout
            .build();

        return Session.create(params);
    }

    @Override
    public Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }
}
