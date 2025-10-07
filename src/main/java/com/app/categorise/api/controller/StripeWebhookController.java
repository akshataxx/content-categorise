package com.app.categorise.api.controller;

import com.app.categorise.domain.model.Subscription;
import com.app.categorise.domain.service.SubscriptionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for handling Stripe webhooks
 */
@RestController
@RequestMapping("/api/webhooks")
@Hidden // Hide from Swagger documentation
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    private final SubscriptionService subscriptionService;

    @Value("${stripe.webhook.secret:whsec_}")
    private String webhookSecret;

    public StripeWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.warn("Invalid signature for Stripe webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        logger.info("Received Stripe webhook event: {}", event.getType());

        // Handle the event
        switch (event.getType()) {
            case "checkout.session.completed":
                handleCheckoutSessionCompleted(event);
                break;
            case "customer.subscription.deleted":
                handleSubscriptionDeleted(event);
                break;
            default:
                logger.info("Unhandled event type: {}", event.getType());
        }

        return ResponseEntity.ok("success");
    }

    private void handleCheckoutSessionCompleted(Event event) {
        try {
            StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
            if (stripeObject instanceof Session session) {
                String clientReferenceId = session.getClientReferenceId();

                if (clientReferenceId != null) {
                    UUID userId = UUID.fromString(clientReferenceId);
                    String subscriptionId = session.getSubscription();

                    // Retrieve the full subscription to get price details
                    com.stripe.model.Subscription stripeSubscription =
                        com.stripe.model.Subscription.retrieve(subscriptionId);

                    String priceId = stripeSubscription.getItems().getData().get(0).getPrice().getId();
                    String customerId = stripeSubscription.getCustomer();

                    // Determine subscription type from price ID
                    Subscription.SubscriptionType type = determinePlanType(priceId);

                    // Upgrade user to premium with Stripe details
                    subscriptionService.upgradeToPremiumWithStripe(
                        userId,
                        customerId,
                        subscriptionId,
                        priceId,
                        type
                    );

                    logger.info("Successfully upgraded user {} to {} subscription", userId, type);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing checkout session completed event", e);
        }
    }

    private Subscription.SubscriptionType determinePlanType(String priceId) {
        // Map Stripe price IDs to subscription types
        // For MVP, we only have monthly plan
        return switch (priceId) {
            case "price_1SDccWBIj51ZSIefUfPLTqxf" -> Subscription.SubscriptionType.PREMIUM_MONTHLY;
            // Add yearly plan when created: case "price_xxx" -> Subscription.SubscriptionType.PREMIUM_YEARLY;
            default -> {
                logger.warn("Unknown price ID: {}, defaulting to PREMIUM_MONTHLY", priceId);
                yield Subscription.SubscriptionType.PREMIUM_MONTHLY;
            }
        };
    }

    private void handleSubscriptionDeleted(Event event) {
        try {
            // Handle subscription cancellation
            logger.info("Subscription deleted event received");
            // TODO: Implement subscription cancellation logic if needed
        } catch (Exception e) {
            logger.error("Error processing subscription deleted event", e);
        }
    }
}