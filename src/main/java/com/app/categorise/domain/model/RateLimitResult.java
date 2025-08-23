package com.app.categorise.domain.model;

import java.time.Instant;

/**
 * RateLimitResult - Domain model representing the result of a rate limit check
 * Contains information about whether a request is allowed and relevant metadata
 */
public class RateLimitResult {
    private boolean allowed;
    private String reason;
    private int remainingRequests;
    private Instant resetTime;
    private RateLimitType limitType;

    public enum RateLimitType {
        PER_MINUTE, PER_DAY, TOTAL
    }

    public RateLimitResult() {}

    public RateLimitResult(boolean allowed, String reason, int remainingRequests, 
                          Instant resetTime, RateLimitType limitType) {
        this.allowed = allowed;
        this.reason = reason;
        this.remainingRequests = remainingRequests;
        this.resetTime = resetTime;
        this.limitType = limitType;
    }

    // Static factory methods for common scenarios
    public static RateLimitResult allowed(int remainingRequests, Instant resetTime, RateLimitType limitType) {
        return new RateLimitResult(true, null, remainingRequests, resetTime, limitType);
    }

    public static RateLimitResult denied(String reason, RateLimitType limitType, Instant resetTime) {
        return new RateLimitResult(false, reason, 0, resetTime, limitType);
    }

    public static RateLimitResult denied(String reason, RateLimitType limitType) {
        return new RateLimitResult(false, reason, 0, null, limitType);
    }

    // Getters and Setters
    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getRemainingRequests() {
        return remainingRequests;
    }

    public void setRemainingRequests(int remainingRequests) {
        this.remainingRequests = remainingRequests;
    }

    public Instant getResetTime() {
        return resetTime;
    }

    public void setResetTime(Instant resetTime) {
        this.resetTime = resetTime;
    }

    public RateLimitType getLimitType() {
        return limitType;
    }

    public void setLimitType(RateLimitType limitType) {
        this.limitType = limitType;
    }

    @Override
    public String toString() {
        return "RateLimitResult{" +
                "allowed=" + allowed +
                ", reason='" + reason + '\'' +
                ", remainingRequests=" + remainingRequests +
                ", resetTime=" + resetTime +
                ", limitType=" + limitType +
                '}';
    }
}