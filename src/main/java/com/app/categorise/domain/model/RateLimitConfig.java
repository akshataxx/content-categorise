package com.app.categorise.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * RateLimitConfig - Domain model for user rate limit configurations
 * Contains the rate limiting thresholds for a specific user
 */
public class RateLimitConfig {
    private UUID id;
    private UUID userId;
    private int transcriptsPerMinuteLimit;
    private int transcriptsPerDayLimit;
    private int totalTranscriptsLimit;
    private Instant createdAt;
    private Instant updatedAt;

    public RateLimitConfig() {}

    public RateLimitConfig(UUID id, UUID userId, int transcriptsPerMinuteLimit, 
                          int transcriptsPerDayLimit, int totalTranscriptsLimit,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.transcriptsPerMinuteLimit = transcriptsPerMinuteLimit;
        this.transcriptsPerDayLimit = transcriptsPerDayLimit;
        this.totalTranscriptsLimit = totalTranscriptsLimit;
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

    public int getTranscriptsPerMinuteLimit() {
        return transcriptsPerMinuteLimit;
    }

    public void setTranscriptsPerMinuteLimit(int transcriptsPerMinuteLimit) {
        this.transcriptsPerMinuteLimit = transcriptsPerMinuteLimit;
    }

    public int getTranscriptsPerDayLimit() {
        return transcriptsPerDayLimit;
    }

    public void setTranscriptsPerDayLimit(int transcriptsPerDayLimit) {
        this.transcriptsPerDayLimit = transcriptsPerDayLimit;
    }

    public int getTotalTranscriptsLimit() {
        return totalTranscriptsLimit;
    }

    public void setTotalTranscriptsLimit(int totalTranscriptsLimit) {
        this.totalTranscriptsLimit = totalTranscriptsLimit;
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
        return "RateLimitConfig{" +
                "id=" + id +
                ", userId=" + userId +
                ", transcriptsPerMinuteLimit=" + transcriptsPerMinuteLimit +
                ", transcriptsPerDayLimit=" + transcriptsPerDayLimit +
                ", totalTranscriptsLimit=" + totalTranscriptsLimit +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}