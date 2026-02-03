package com.app.categorise.domain.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Service for verifying and managing Google Play Billing subscriptions.
 * Uses the Google Play Developer API to verify purchase tokens and acknowledge subscriptions.
 */
@Service
public class GooglePlayBillingService {

    private static final Logger logger = LoggerFactory.getLogger(GooglePlayBillingService.class);
    private static final String APPLICATION_NAME = "TranscribeAssistant";

    @Value("${google.play.package-name}")
    private String packageName;

    @Value("${google.play.service-account-key-path:}")
    private String serviceAccountKeyPath;

    @Value("${google.play.product-id.monthly}")
    private String monthlyProductId;

    private AndroidPublisher androidPublisher;

    @PostConstruct
    public void init() {
        try {
            if (serviceAccountKeyPath != null && !serviceAccountKeyPath.isEmpty()) {
                GoogleCredentials credentials = GoogleCredentials
                        .fromStream(new FileInputStream(serviceAccountKeyPath))
                        .createScoped(Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER));

                androidPublisher = new AndroidPublisher.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(credentials))
                        .setApplicationName(APPLICATION_NAME)
                        .build();

                logger.info("Google Play Billing service initialized successfully");
            } else {
                logger.warn("Google Play service account key path not configured. " +
                        "Purchase verification will not work until configured.");
            }
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Failed to initialize Google Play Billing service", e);
            throw new RuntimeException("Failed to initialize Google Play Billing service", e);
        }
    }

    /**
     * Verify a subscription purchase with Google Play.
     *
     * @param productId     the product ID (e.g., "premium_monthly")
     * @param purchaseToken the purchase token from the Android app
     * @return the subscription purchase details from Google Play
     * @throws IOException if verification fails
     */
    public SubscriptionPurchase verifySubscription(String productId, String purchaseToken) throws IOException {
        if (androidPublisher == null) {
            throw new IllegalStateException("Google Play Billing service not initialized");
        }

        logger.info("Verifying subscription: productId={}, packageName={}", productId, packageName);

        SubscriptionPurchase purchase = androidPublisher.purchases().subscriptions()
                .get(packageName, productId, purchaseToken)
                .execute();

        logger.info("Subscription verification result: paymentState={}, acknowledgementState={}, expiryTimeMillis={}",
                purchase.getPaymentState(), purchase.getAcknowledgementState(), purchase.getExpiryTimeMillis());

        return purchase;
    }

    /**
     * Acknowledge a subscription purchase.
     * Subscriptions must be acknowledged within 3 days or they will be refunded.
     *
     * @param productId     the product ID
     * @param purchaseToken the purchase token
     * @throws IOException if acknowledgement fails
     */
    public void acknowledgeSubscription(String productId, String purchaseToken) throws IOException {
        if (androidPublisher == null) {
            throw new IllegalStateException("Google Play Billing service not initialized");
        }

        logger.info("Acknowledging subscription: productId={}", productId);

        SubscriptionPurchasesAcknowledgeRequest request = new SubscriptionPurchasesAcknowledgeRequest();

        androidPublisher.purchases().subscriptions()
                .acknowledge(packageName, productId, purchaseToken, request)
                .execute();

        logger.info("Subscription acknowledged successfully");
    }

    /**
     * Check if a subscription is currently active.
     *
     * @param purchase the subscription purchase to check
     * @return true if the subscription is active
     */
    public boolean isSubscriptionActive(SubscriptionPurchase purchase) {
        if (purchase == null) {
            return false;
        }

        // Check payment state (0 = pending, 1 = received, 2 = free trial, 3 = pending deferred upgrade/downgrade)
        Integer paymentState = purchase.getPaymentState();
        if (paymentState == null || paymentState == 0) {
            return false;
        }

        // Check if not expired
        Long expiryTimeMillis = purchase.getExpiryTimeMillis();
        if (expiryTimeMillis != null && expiryTimeMillis < System.currentTimeMillis()) {
            return false;
        }

        // Check cancel reason (0 = user canceled, 1 = system canceled, 2 = replaced, 3 = developer canceled)
        // If canceled but not expired, user still has access until expiry
        return true;
    }

    /**
     * Check if a subscription needs to be acknowledged.
     *
     * @param purchase the subscription purchase to check
     * @return true if acknowledgement is needed
     */
    public boolean needsAcknowledgement(SubscriptionPurchase purchase) {
        if (purchase == null) {
            return false;
        }
        // 0 = not acknowledged, 1 = acknowledged
        Integer acknowledgementState = purchase.getAcknowledgementState();
        return acknowledgementState == null || acknowledgementState == 0;
    }

    public String getMonthlyProductId() {
        return monthlyProductId;
    }
}
