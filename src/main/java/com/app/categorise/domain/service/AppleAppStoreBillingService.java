package com.app.categorise.domain.service;

import com.app.categorise.api.dto.subscription.apple.AppStoreVerificationResult;
import com.app.categorise.api.dto.subscription.apple.DecodedTransaction;
import com.app.categorise.api.dto.subscription.apple.AppStoreSubscriptionStatus;

/**
 * Service for verifying and managing Apple App Store subscriptions.
 */
public interface AppleAppStoreBillingService {

    /**
     * Verify a signed transaction from StoreKit 2.
     * Decodes the JWS, validates the signature, and returns subscription details.
     *
     * @param signedTransaction JWS transaction string from iOS app
     * @return Verification result with subscription status
     */
    AppStoreVerificationResult verifyTransaction(String signedTransaction);

    /**
     * Decode a JWS-signed payload without calling Apple's API.
     * Used for extracting transaction info from webhooks.
     *
     * @param signedPayload JWS from Apple
     * @return Decoded transaction information
     */
    DecodedTransaction decodeTransaction(String signedPayload);

    /**
     * Get current subscription status from App Store Server API.
     *
     * @param originalTransactionId Original transaction ID
     * @return Current subscription status from Apple
     */
    AppStoreSubscriptionStatus getSubscriptionStatus(String originalTransactionId);
}
