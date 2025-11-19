package com.app.categorise.api.controller;

import com.app.categorise.api.dto.subscription.SubscriptionDto;
import com.app.categorise.api.dto.subscription.StripeCheckoutRequest;
import com.app.categorise.api.dto.subscription.StripeCheckoutResponse;
import com.app.categorise.api.dto.subscription.UsageInfoDto;
import com.app.categorise.domain.model.UserSubscription;
import com.app.categorise.domain.service.PaymentService;
import com.app.categorise.domain.service.SubscriptionService;
import com.app.categorise.domain.service.UsageService;
import com.app.categorise.exception.PaymentException;
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
    private final PaymentService paymentService;
    private final UsageService usageService;

    public SubscriptionController(SubscriptionService subscriptionService,
                                 PaymentService paymentService,
                                 UsageService usageService) {
        this.subscriptionService = subscriptionService;
        this.paymentService = paymentService;
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
    
    @Operation(summary = "Create Stripe checkout session")
    @PostMapping("/create-checkout")
    public ResponseEntity<StripeCheckoutResponse> createCheckout(
            @Valid @RequestBody StripeCheckoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        try {
            UUID userId = principal.getId();
            Session session = paymentService.createCheckoutSession(userId, request.getPriceId());

            StripeCheckoutResponse response = new StripeCheckoutResponse(
                session.getUrl(),
                session.getId()
            );

            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            throw new PaymentException("Failed to create checkout session", e);
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