package com.app.categorise.api.controller;

import com.app.categorise.api.dto.subscription.SubscriptionDto;
import com.app.categorise.api.dto.subscription.StripeCheckoutRequest;
import com.app.categorise.api.dto.subscription.StripeCheckoutResponse;
import com.app.categorise.api.dto.subscription.UpgradeSubscriptionRequest;
import com.app.categorise.api.dto.subscription.UsageInfoDto;
import com.app.categorise.application.internal.StripeService;
import com.app.categorise.domain.model.Subscription;
import com.app.categorise.domain.service.SubscriptionService;
import com.app.categorise.security.UserPrincipal;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for subscription management
 */
@RestController
@RequestMapping("/api/subscription")
@Tag(name = "Subscription", description = "Operations related to user subscriptions and billing")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;

    public SubscriptionController(SubscriptionService subscriptionService, StripeService stripeService) {
        this.subscriptionService = subscriptionService;
        this.stripeService = stripeService;
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
        
        boolean isPremium = subscriptionService.hasActivePremiumSubscription(userId);
        int remainingFree = subscriptionService.getRemainingFreeTranscriptions(userId);
        
        UsageInfoDto usage = new UsageInfoDto(isPremium, remainingFree);
        return ResponseEntity.ok(usage);
    }
    
    @Operation(summary = "Upgrade to premium subscription")
    @PostMapping("/upgrade")
    public ResponseEntity<SubscriptionDto> upgradeSubscription(
            @Valid @RequestBody UpgradeSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        UUID userId = principal.getId();
        
        // Verify the Google Play purchase
        boolean isValidPurchase = subscriptionService.verifyGooglePlayPurchase(
                request.getPurchaseToken(), request.getProductId());
        
        if (!isValidPurchase) {
            return ResponseEntity.badRequest().build();
        }
        
        Subscription.SubscriptionType type = mapSubscriptionType(request.getProductId());
        Subscription upgraded = subscriptionService.upgradeToPremium(
                userId, request.getPurchaseToken(), request.getProductId(), type);
        
        return ResponseEntity.ok(mapToDto(upgraded));
    }
    
    @Operation(summary = "Create Stripe checkout session")
    @PostMapping("/create-checkout")
    public ResponseEntity<StripeCheckoutResponse> createCheckout(
            @Valid @RequestBody StripeCheckoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        try {
            UUID userId = principal.getId();
            Session session = stripeService.createCheckoutSession(userId, request.getPriceId());

            StripeCheckoutResponse response = new StripeCheckoutResponse(
                session.getUrl(),
                session.getId()
            );

            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Cancel subscription")
    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelSubscription(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getId();
        subscriptionService.cancelSubscription(userId);
        return ResponseEntity.noContent().build();
    }
    
    private SubscriptionDto mapToDto(Subscription subscription) {
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
    
    private Subscription.SubscriptionType mapSubscriptionType(String productId) {
        return switch (productId) {
            case "premium_monthly" -> Subscription.SubscriptionType.PREMIUM_MONTHLY;
            case "premium_yearly" -> Subscription.SubscriptionType.PREMIUM_YEARLY;
            default -> throw new IllegalArgumentException("Unknown product ID: " + productId);
        };
    }
}