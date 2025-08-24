package com.app.categorise.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * RateLimitUsage - Domain model for tracking rate limit usage in time windows
 * Tracks how many requests a user has made in a specific time window
 */
public class RateLimitUsage {
    private UUID id;
    private UUID userId;
    private Instant windowStart;
    private WindowType windowType;
    private int requestCount;
    private Instant createdAt;
    private Instant updatedAt;

    public enum WindowType {
        MINUTE, DAY
    }

    public RateLimitUsage() {}

    public RateLimitUsage(UUID id, UUID userId, Instant windowStart, WindowType windowType,
                         int requestCount, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.windowStart = windowStart;
        this.windowType = windowType;
        this.requestCount = requestCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public WindowType getWindowType() {
        return windowType;
    }

    public void setWindowType(WindowType windowType) {
        this.windowType = windowType;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "RateLimitUsage{" +
                "id=" + id +
                ", userId=" + userId +
                ", windowStart=" + windowStart +
                ", windowType=" + windowType +
                ", requestCount=" + requestCount +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}