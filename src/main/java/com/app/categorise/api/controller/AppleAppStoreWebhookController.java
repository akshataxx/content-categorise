package com.app.categorise.api.controller;

import com.app.categorise.api.dto.subscription.apple.AppStoreNotificationPayload;
import com.app.categorise.api.dto.subscription.apple.AppStoreNotificationType;
import com.app.categorise.api.dto.subscription.apple.DecodedNotification;
import com.app.categorise.api.dto.subscription.apple.DecodedTransaction;
import com.app.categorise.config.AppleAppStoreConfiguration;
import com.app.categorise.data.entity.ProcessedNotificationEntity;
import com.app.categorise.data.entity.UserSubscriptionEntity;
import com.app.categorise.data.repository.ProcessedNotificationRepository;
import com.app.categorise.data.repository.UserSubscriptionRepository;
import com.app.categorise.domain.service.AppleAppStoreBillingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Webhook controller for Apple App Store Server Notifications v2 (ASN v2).
 * Handles subscription lifecycle events from Apple.
 */
@RestController
@RequestMapping("/api/webhook")
@Tag(name = "Webhooks", description = "Webhook endpoints for external service notifications")
public class AppleAppStoreWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(AppleAppStoreWebhookController.class);

    private final AppleAppStoreBillingService billingService;
    private final UserSubscriptionRepository subscriptionRepository;
    private final ProcessedNotificationRepository processedNotificationRepository;
    private final AppleAppStoreConfiguration config;
    private final ObjectMapper objectMapper;

    public AppleAppStoreWebhookController(AppleAppStoreBillingService billingService,
                                          UserSubscriptionRepository subscriptionRepository,
                                          ProcessedNotificationRepository processedNotificationRepository,
                                          AppleAppStoreConfiguration config,
                                          ObjectMapper objectMapper) {
        this.billingService = billingService;
        this.subscriptionRepository = subscriptionRepository;
        this.processedNotificationRepository = processedNotificationRepository;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives App Store Server Notifications v2 from Apple.
     * Apple expects a 200 response within 60 seconds.
     * Any non-2xx response triggers Apple to retry.
     */
    @Operation(summary = "Handle Apple App Store Server Notification v2")
    @PostMapping("/app-store")
    public ResponseEntity<Void> handleNotification(
            @RequestBody AppStoreNotificationPayload payload) {

        String correlationId = UUID.randomUUID().toString();
        logger.info("App Store webhook received: correlationId={}", correlationId);

        try {
            processNotification(payload.getSignedPayload());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Log error but still return 200 to prevent infinite retries
            logger.error("Error processing App Store notification: correlationId={}", correlationId, e);
            return ResponseEntity.ok().build();
        }
    }

    private void processNotification(String signedPayload) {
        DecodedNotification notification = decodeNotificationPayload(signedPayload);

        // Idempotency check using notification UUID
        String notificationId = notification.getNotificationUUID();
        if (notificationId != null && processedNotificationRepository.existsByNotificationId(notificationId)) {
            logger.info("Skipping already processed notification: {}", notificationId);
            return;
        }

        logger.info("Processing notification: type={}, subtype={}, uuid={}, env={}",
                notification.getNotificationType(),
                notification.getSubtype(),
                notification.getNotificationUUID(),
                notification.getEnvironment());

        // Validate environment matches our configuration
        if (!isValidEnvironment(notification.getEnvironment())) {
            logger.warn("Ignoring notification from wrong environment: {}", notification.getEnvironment());
            return;
        }

        // Decode transaction info if present
        DecodedTransaction transaction = null;
        if (notification.getSignedTransactionInfo() != null) {
            transaction = billingService.decodeTransaction(notification.getSignedTransactionInfo());
        }

        // Route to appropriate handler
        try {
            AppStoreNotificationType type = AppStoreNotificationType.valueOf(
                    notification.getNotificationType());
            routeNotification(type, notification, transaction);
        } catch (IllegalArgumentException e) {
            logger.info("Unknown notification type: {}", notification.getNotificationType());
        }

        // Mark as processed
        if (notificationId != null) {
            processedNotificationRepository.save(new ProcessedNotificationEntity(
                    notificationId,
                    notification.getNotificationType(),
                    "APP_STORE"
            ));
        }

        logger.info("Notification processed successfully: uuid={}", notificationId);
    }

    private void routeNotification(AppStoreNotificationType type,
                                   DecodedNotification notification,
                                   DecodedTransaction transaction) {
        switch (type) {
            case SUBSCRIBED:
                handleSubscribed(notification, transaction);
                break;
            case DID_RENEW:
                handleRenewal(transaction);
                break;
            case DID_CHANGE_RENEWAL_STATUS:
                handleRenewalStatusChange(notification, transaction);
                break;
            case EXPIRED:
            case GRACE_PERIOD_EXPIRED:
                handleExpired(transaction);
                break;
            case REFUND:
            case REVOKE:
                handleRevoked(transaction);
                break;
            case DID_FAIL_TO_RENEW:
                handleFailedRenewal(notification, transaction);
                break;
            case TEST:
                logger.info("Received TEST notification - Apple webhook connectivity verified");
                break;
            default:
                logger.info("Unhandled notification type: {}", type);
        }
    }

    private void handleSubscribed(DecodedNotification notification, DecodedTransaction tx) {
        if (tx == null) return;
        logger.info("Handling SUBSCRIBED: subtype={}, originalTxId={}",
                notification.getSubtype(), tx.getOriginalTransactionId());
        // Subscription creation is handled by the verify endpoint (WP05).
        // This notification serves as backup confirmation.
    }

    private void handleRenewal(DecodedTransaction tx) {
        if (tx == null) return;
        logger.info("Handling DID_RENEW for originalTxId={}", tx.getOriginalTransactionId());

        Optional<UserSubscriptionEntity> subscriptionOpt =
                subscriptionRepository.findByAppleOriginalTransactionId(tx.getOriginalTransactionId());

        if (subscriptionOpt.isEmpty()) {
            logger.warn("No subscription found for Apple originalTxId: {}", tx.getOriginalTransactionId());
            return;
        }

        UserSubscriptionEntity entity = subscriptionOpt.get();
        entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.ACTIVE);
        entity.setAppleTransactionId(tx.getTransactionId());
        entity.setAutoRenew(true);
        if (tx.getExpiresDate() != null) {
            entity.setSubscriptionEndDate(tx.getExpiresDate());
        }
        subscriptionRepository.save(entity);

        logger.info("Subscription renewed for user: {}", entity.getUserId());
    }

    private void handleRenewalStatusChange(DecodedNotification notification, DecodedTransaction tx) {
        if (tx == null) return;
        logger.info("Handling DID_CHANGE_RENEWAL_STATUS: subtype={}", notification.getSubtype());

        Optional<UserSubscriptionEntity> subscriptionOpt =
                subscriptionRepository.findByAppleOriginalTransactionId(tx.getOriginalTransactionId());

        if (subscriptionOpt.isEmpty()) {
            logger.warn("No subscription found for renewal status change: {}", tx.getOriginalTransactionId());
            return;
        }

        UserSubscriptionEntity entity = subscriptionOpt.get();
        boolean autoRenewEnabled = "AUTO_RENEW_ENABLED".equals(notification.getSubtype());
        entity.setAutoRenew(autoRenewEnabled);

        if (!autoRenewEnabled) {
            // User turned off auto-renew (cancelled). They keep access until end date.
            entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.CANCELLED);
        } else {
            // User re-enabled auto-renew
            entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.ACTIVE);
        }
        subscriptionRepository.save(entity);

        logger.info("Auto-renew {} for user: {}", autoRenewEnabled ? "enabled" : "disabled", entity.getUserId());
    }

    private void handleExpired(DecodedTransaction tx) {
        if (tx == null) return;
        logger.info("Handling EXPIRED for originalTxId={}", tx.getOriginalTransactionId());

        Optional<UserSubscriptionEntity> subscriptionOpt =
                subscriptionRepository.findByAppleOriginalTransactionId(tx.getOriginalTransactionId());

        if (subscriptionOpt.isEmpty()) {
            logger.warn("No subscription found for expired notification: {}", tx.getOriginalTransactionId());
            return;
        }

        UserSubscriptionEntity entity = subscriptionOpt.get();
        entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.EXPIRED);
        entity.setAutoRenew(false);
        subscriptionRepository.save(entity);

        logger.info("Subscription expired for user: {}", entity.getUserId());
    }

    private void handleRevoked(DecodedTransaction tx) {
        if (tx == null) return;
        logger.info("Handling REFUND/REVOKE for originalTxId={}", tx.getOriginalTransactionId());

        Optional<UserSubscriptionEntity> subscriptionOpt =
                subscriptionRepository.findByAppleOriginalTransactionId(tx.getOriginalTransactionId());

        if (subscriptionOpt.isEmpty()) {
            logger.warn("No subscription found for revoke: {}", tx.getOriginalTransactionId());
            return;
        }

        UserSubscriptionEntity entity = subscriptionOpt.get();
        entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.EXPIRED);
        entity.setAutoRenew(false);
        subscriptionRepository.save(entity);

        logger.info("Subscription revoked for user: {}", entity.getUserId());
    }

    private void handleFailedRenewal(DecodedNotification notification, DecodedTransaction tx) {
        if (tx == null) return;
        logger.info("Handling DID_FAIL_TO_RENEW: subtype={}, originalTxId={}",
                notification.getSubtype(), tx.getOriginalTransactionId());
        // User still has access during grace period / billing retry.
        // Just log for monitoring - Apple will send EXPIRED if recovery fails.
    }

    /**
     * Decode the JWS-signed notification payload from Apple.
     * For MVP, we decode without full certificate chain verification,
     * relying on HTTPS transport security.
     */
    private DecodedNotification decodeNotificationPayload(String signedPayload) {
        try {
            String[] parts = signedPayload.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWS format: expected 3 parts, got " + parts.length);
            }

            // Decode payload (middle part)
            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            JsonNode payload = objectMapper.readTree(payloadJson);
            JsonNode data = payload.path("data");

            return DecodedNotification.builder()
                    .notificationType(getTextOrNull(payload, "notificationType"))
                    .subtype(getTextOrNull(payload, "subtype"))
                    .notificationUUID(getTextOrNull(payload, "notificationUUID"))
                    .environment(getTextOrNull(data, "environment"))
                    .bundleId(getTextOrNull(data, "bundleId"))
                    .signedTransactionInfo(getTextOrNull(data, "signedTransactionInfo"))
                    .signedRenewalInfo(getTextOrNull(data, "signedRenewalInfo"))
                    .build();

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to decode notification payload", e);
            throw new RuntimeException("Invalid notification payload", e);
        }
    }

    private boolean isValidEnvironment(String environment) {
        if (environment == null) return false;
        String expected = config.getEnvironment();
        // Accept "Sandbox" or "sandbox" from Apple
        return expected.equalsIgnoreCase(environment);
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
