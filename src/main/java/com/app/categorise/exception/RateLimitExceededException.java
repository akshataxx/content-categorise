package com.app.categorise.exception;

import com.app.categorise.domain.model.RateLimitResult;

/**
 * RateLimitExceededException - Custom exception thrown when rate limits are exceeded
 * Contains the RateLimitResult with detailed information about the violation
 */
public class RateLimitExceededException extends RuntimeException {
    
    private final RateLimitResult rateLimitResult;

    public RateLimitExceededException(RateLimitResult rateLimitResult) {
        super(rateLimitResult.getReason());
        this.rateLimitResult = rateLimitResult;
    }

    public RateLimitExceededException(String message, RateLimitResult rateLimitResult) {
        super(message);
        this.rateLimitResult = rateLimitResult;
    }

    public RateLimitExceededException(String message, Throwable cause, RateLimitResult rateLimitResult) {
        super(message, cause);
        this.rateLimitResult = rateLimitResult;
    }

    /**
     * Get the rate limit result containing details about the violation
     * @return RateLimitResult with violation details
     */
    public RateLimitResult getRateLimitResult() {
        return rateLimitResult;
    }

    /**
     * Get the type of rate limit that was exceeded
     * @return RateLimitType indicating which limit was exceeded
     */
    public RateLimitResult.RateLimitType getLimitType() {
        return rateLimitResult.getLimitType();
    }

    /**
     * Get the number of remaining requests (typically 0 for exceeded limits)
     * @return Number of remaining requests
     */
    public int getRemainingRequests() {
        return rateLimitResult.getRemainingRequests();
    }

    /**
     * Get the time when the rate limit will reset
     * @return Reset time as Instant, or null if not applicable
     */
    public java.time.Instant getResetTime() {
        return rateLimitResult.getResetTime();
    }

    @Override
    public String toString() {
        return "RateLimitExceededException{" +
                "rateLimitResult=" + rateLimitResult +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}