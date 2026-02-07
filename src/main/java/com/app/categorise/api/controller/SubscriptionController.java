package com.app.categorise.api.controller;

import com.app.categorise.api.dto.appstore.AppStoreVerificationRequest;
import com.app.categorise.api.dto.appstore.AppStoreVerificationResponse;
import com.app.categorise.api.dto.appstore.AppStoreVerificationResult;
import com.app.categorise.api.dto.subscription.GooglePlayPurchaseVerificationRequest;
import com.app.categorise.api.dto.subscription.GooglePlayVerificationResponse;
import com.app.categorise.api.dto.subscription.SubscriptionDto;
import com.app.categorise.api.dto.subscription.UsageInfoDto;
import com.app.categorise.domain.model.UserSubscription;
import com.app.categorise.domain.service.AppleAppStoreBillingService;
import com.app.categorise.domain.service.GooglePlayBillingService;
import com.app.categorise.domain.service.SubscriptionService;
import com.app.categorise.domain.service.UsageService;
import com.app.categorise.security.UserPrincipal;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for subscription management
 */
@RestController
@RequestMapping("/api/subscription")
@Tag(name = "Subscription", description = "Operations related to user subscriptions and billing")
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;
    private final GooglePlayBillingService googlePlayBillingService;
    private final AppleAppStoreBillingService appleAppStoreBillingService;
    private final UsageService usageService;

    public SubscriptionController(SubscriptionService subscriptionService,
                                 GooglePlayBillingService googlePlayBillingService,
                                 AppleAppStoreBillingService appleAppStoreBillingService,
                                 UsageService usageService) {
        this.subscriptionService = subscriptionService;
        this.googlePlayBillingService = googlePlayBillingService;
        this.appleAppStoreBillingService = appleAppStoreBillingService;
        this.usageService = usageService;
    }

    @Operation(summary = "Get user subscription status")
    @GetMapping("/status")
    public ResponseEntity<SubscriptionDto> getSubscriptionStatus(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getId();

        return subscriptionService.getUserSubscription(userId)
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get usage information")
    @GetMapping("/usage")
    public ResponseEntity<UsageInfoDto> getUsageInfo(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getId();
        UsageInfoDto usage = usageService.getUserUsageInfo(userId);
        return ResponseEntity.ok(usage);
    }

    @Operation(summary = "Verify Google Play purchase and activate subscription")
    @PostMapping("/google-play/verify")
    public ResponseEntity<GooglePlayVerificationResponse> verifyGooglePlayPurchase(
            @Valid @RequestBody GooglePlayPurchaseVerificationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getId();
        logger.info("Verifying Google Play purchase for user {}: productId={}", userId, request.getProductId());

        try {
            // Verify purchase with Google Play
            SubscriptionPurchase purchase = googlePlayBillingService.verifySubscription(
                    request.getProductId(),
                    request.getPurchaseToken()
            );

            // Check if subscription is active
            boolean isActive = googlePlayBillingService.isSubscriptionActive(purchase);

            if (!isActive) {
                logger.warn("Google Play subscription not active for user {}", userId);
                return ResponseEntity.ok(GooglePlayVerificationResponse.failure("Subscription is not active"));
            }

            // Acknowledge the purchase if needed
            if (googlePlayBillingService.needsAcknowledgement(purchase)) {
                googlePlayBillingService.acknowledgeSubscription(
                        request.getProductId(),
                        request.getPurchaseToken()
                );
                logger.info("Acknowledged Google Play subscription for user {}", userId);
            }

            // Determine subscription type from product ID
            UserSubscription.SubscriptionType subscriptionType =
                    request.getProductId().contains("yearly")
                            ? UserSubscription.SubscriptionType.PREMIUM_YEARLY
                            : UserSubscription.SubscriptionType.PREMIUM_MONTHLY;

            // Upgrade user subscription in our database
            subscriptionService.upgradeToPremiumWithGooglePlay(
                    userId,
                    request.getPurchaseToken(),
                    request.getProductId(),
                    request.getOrderId(),
                    subscriptionType
            );

            // Calculate expiration time
            Instant expirationTime = purchase.getExpiryTimeMillis() != null
                    ? Instant.ofEpochMilli(purchase.getExpiryTimeMillis())
                    : null;

            logger.info("Successfully verified and activated Google Play subscription for user {}", userId);
            return ResponseEntity.ok(GooglePlayVerificationResponse.success(true, expirationTime));

        } catch (IOException e) {
            logger.error("Failed to verify Google Play purchase for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.ok(GooglePlayVerificationResponse.failure("Failed to verify purchase: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error verifying Google Play purchase for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.ok(GooglePlayVerificationResponse.failure("An unexpected error occurred"));
        }
    }

    @Operation(summary = "Verify App Store purchase and activate subscription")
    @PostMapping("/app-store/verify")
    public ResponseEntity<AppStoreVerificationResponse> verifyAppStorePurchase(
            @Valid @RequestBody AppStoreVerificationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getId();
        logger.info("Verifying App Store purchase for user {}: productId={}", userId, request.getProductId());

        try {
            // Verify with Apple
            AppStoreVerificationResult result = appleAppStoreBillingService
                    .verifyTransaction(request.getSignedTransaction());

            if (result.isVerified() && result.isSubscriptionActive()) {
                // Upgrade user to premium
                subscriptionService.upgradeToPremiumWithAppStore(
                        userId,
                        result.getOriginalTransactionId(),
                        result.getTransactionId(),
                        result.getProductId(),
                        result.getExpirationTime()
                );

                logger.info("Successfully verified and activated App Store subscription for user {}", userId);
                return ResponseEntity.ok(AppStoreVerificationResponse.success(true, result.getExpirationTime()));
            } else {
                logger.warn("App Store subscription verification failed for user {}: {}",
                        userId, result.getErrorMessage());
                return ResponseEntity.ok(AppStoreVerificationResponse.failure(
                        result.getErrorMessage() != null ? result.getErrorMessage() : "Subscription not active"));
            }

        } catch (Exception e) {
            logger.error("Unexpected error verifying App Store purchase for user {}: {}",
                    userId, e.getMessage(), e);
            return ResponseEntity.ok(AppStoreVerificationResponse.failure("An unexpected error occurred"));
        }
    }

    @Operation(summary = "Cancel subscription")
    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelSubscription(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getId();
        subscriptionService.cancelSubscription(userId);
        return ResponseEntity.noContent().build();
    }

    private SubscriptionDto mapToDto(UserSubscription subscription) {
        return new SubscriptionDto(
                subscription.getId(),
                subscription.getUserId(),
                subscription.getSubscriptionType().name(),
                subscription.getStatus().name(),
                subscription.getSubscriptionStartDate(),
                subscription.getSubscriptionEndDate(),
                subscription.isAutoRenew(),
                subscription.isPremium()
        );
    }
}
