package com.app.categorise.api.controller;

import com.app.categorise.data.entity.UserSubscriptionEntity;
import com.app.categorise.data.repository.UserSubscriptionRepository;
import com.app.categorise.domain.service.GooglePlayBillingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Webhook controller for Google Play Real-time Developer Notifications (RTDN).
 * Handles subscription lifecycle events from Google Play.
 */
@RestController
@RequestMapping("/api/webhook")
@Tag(name = "Webhooks", description = "Webhook endpoints for external service notifications")
public class GooglePlayWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(GooglePlayWebhookController.class);

    private final GooglePlayBillingService googlePlayBillingService;
    private final UserSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    // Google Play RTDN notification types for subscriptions
    private static final int SUBSCRIPTION_RECOVERED = 1;
    private static final int SUBSCRIPTION_RENEWED = 2;
    private static final int SUBSCRIPTION_CANCELED = 3;
    private static final int SUBSCRIPTION_PURCHASED = 4;
    private static final int SUBSCRIPTION_ON_HOLD = 5;
    private static final int SUBSCRIPTION_IN_GRACE_PERIOD = 6;
    private static final int SUBSCRIPTION_RESTARTED = 7;
    private static final int SUBSCRIPTION_PRICE_CHANGE_CONFIRMED = 8;
    private static final int SUBSCRIPTION_DEFERRED = 9;
    private static final int SUBSCRIPTION_PAUSED = 10;
    private static final int SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED = 11;
    private static final int SUBSCRIPTION_REVOKED = 12;
    private static final int SUBSCRIPTION_EXPIRED = 13;

    public GooglePlayWebhookController(GooglePlayBillingService googlePlayBillingService,
                                       UserSubscriptionRepository subscriptionRepository,
                                       ObjectMapper objectMapper) {
        this.googlePlayBillingService = googlePlayBillingService;
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Handle Google Play RTDN webhook")
    @PostMapping("/google-play")
    public ResponseEntity<Void> handleGooglePlayNotification(@RequestBody JsonNode payload) {
        try {
            logger.info("Received Google Play RTDN notification");

            // Parse Pub/Sub message
            JsonNode message = payload.get("message");
            if (message == null) {
                logger.warn("No message in payload");
                return ResponseEntity.ok().build();
            }

            String data = message.get("data").asText();
            byte[] decodedData = Base64.getDecoder().decode(data);
            JsonNode notification = objectMapper.readTree(decodedData);

            logger.info("Decoded notification: {}", notification.toString());

            // Handle subscription notification
            JsonNode subscriptionNotification = notification.get("subscriptionNotification");
            if (subscriptionNotification != null) {
                handleSubscriptionNotification(subscriptionNotification);
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            logger.error("Error processing Google Play notification", e);
            // Return 200 to acknowledge receipt (prevents Pub/Sub retries)
            return ResponseEntity.ok().build();
        }
    }

    private void handleSubscriptionNotification(JsonNode notification) throws IOException {
        int notificationType = notification.get("notificationType").asInt();
        String purchaseToken = notification.get("purchaseToken").asText();
        String subscriptionId = notification.get("subscriptionId").asText();

        logger.info("Processing subscription notification: type={}, subscriptionId={}",
                notificationType, subscriptionId);

        // Find subscription by purchase token
        Optional<UserSubscriptionEntity> subscriptionOpt =
                subscriptionRepository.findByGooglePlayPurchaseToken(purchaseToken);

        switch (notificationType) {
            case SUBSCRIPTION_PURCHASED:
            case SUBSCRIPTION_RENEWED:
            case SUBSCRIPTION_RECOVERED:
            case SUBSCRIPTION_RESTARTED:
                handleSubscriptionActive(purchaseToken, subscriptionId, subscriptionOpt);
                break;

            case SUBSCRIPTION_CANCELED:
                handleSubscriptionCanceled(subscriptionOpt);
                break;

            case SUBSCRIPTION_EXPIRED:
            case SUBSCRIPTION_REVOKED:
                handleSubscriptionExpired(subscriptionOpt);
                break;

            case SUBSCRIPTION_ON_HOLD:
            case SUBSCRIPTION_IN_GRACE_PERIOD:
                handleSubscriptionGracePeriod(subscriptionOpt);
                break;

            case SUBSCRIPTION_PAUSED:
                handleSubscriptionPaused(subscriptionOpt);
                break;

            default:
                logger.info("Unhandled notification type: {}", notificationType);
        }
    }

    private void handleSubscriptionActive(String purchaseToken, String subscriptionId,
                                          Optional<UserSubscriptionEntity> subscriptionOpt) throws IOException {
        if (subscriptionOpt.isEmpty()) {
            logger.warn("No subscription found for purchase token. " +
                    "This may be a new purchase that will be verified by the app.");
            return;
        }

        UserSubscriptionEntity entity = subscriptionOpt.get();

        // Verify with Google Play to get latest state
        SubscriptionPurchase purchase = googlePlayBillingService.verifySubscription(
                subscriptionId, purchaseToken);

        if (googlePlayBillingService.isSubscriptionActive(purchase)) {
            entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.ACTIVE);
            entity.setAutoRenew(true);

            if (purchase.getExpiryTimeMillis() != null) {
                entity.setSubscriptionEndDate(Instant.ofEpochMilli(purchase.getExpiryTimeMillis()));
            }

            subscriptionRepository.save(entity);
            logger.info("Subscription activated/renewed for user: {}", entity.getUserId());
        }
    }

    private void handleSubscriptionCanceled(Optional<UserSubscriptionEntity> subscriptionOpt) {
        if (subscriptionOpt.isEmpty()) {
            logger.warn("No subscription found for canceled notification");
            return;
        }

        UserSubscriptionEntity entity = subscriptionOpt.get();
        entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.CANCELLED);
        entity.setAutoRenew(false);
        subscriptionRepository.save(entity);

        logger.info("Subscription canceled for user: {}. Access continues until end date.", entity.getUserId());
    }

    private void handleSubscriptionExpired(Optional<UserSubscriptionEntity> subscriptionOpt) {
        if (subscriptionOpt.isEmpty()) {
            logger.warn("No subscription found for expired notification");
            return;
        }

        UserSubscriptionEntity entity = subscriptionOpt.get();
        entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.EXPIRED);
        entity.setAutoRenew(false);
        subscriptionRepository.save(entity);

        logger.info("Subscription expired for user: {}", entity.getUserId());
    }

    private void handleSubscriptionGracePeriod(Optional<UserSubscriptionEntity> subscriptionOpt) {
        if (subscriptionOpt.isEmpty()) {
            logger.warn("No subscription found for grace period notification");
            return;
        }

        // Keep subscription active during grace period
        UserSubscriptionEntity entity = subscriptionOpt.get();
        logger.info("Subscription in grace period for user: {}. Keeping active.", entity.getUserId());
    }

    private void handleSubscriptionPaused(Optional<UserSubscriptionEntity> subscriptionOpt) {
        if (subscriptionOpt.isEmpty()) {
            logger.warn("No subscription found for paused notification");
            return;
        }

        UserSubscriptionEntity entity = subscriptionOpt.get();
        entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.PENDING);
        entity.setAutoRenew(false);
        subscriptionRepository.save(entity);

        logger.info("Subscription paused for user: {}", entity.getUserId());
    }
}
