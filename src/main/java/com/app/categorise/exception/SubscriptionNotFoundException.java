package com.app.categorise.exception;

import java.util.UUID;

/**
 * Exception thrown when a subscription is not found
 */
public class SubscriptionNotFoundException extends RuntimeException {

    private final UUID userId;

    public SubscriptionNotFoundException(UUID userId) {
        super("Subscription not found for user: " + userId);
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
