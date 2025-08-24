package com.app.categorise.data.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * UserRateLimitTrackingEntity - JPA entity for user_rate_limit_tracking table
 * Tracks usage for windowed rate limits (per minute and per day)
 */
@Entity
@Table(name = "user_rate_limit_tracking",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "window_start", "window_type"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRateLimitTrackingEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "window_type", nullable = false, length = 20)
    private WindowType windowType;

    @Column(name = "request_count", nullable = false)
    private Integer requestCount;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum WindowType {
        MINUTE, DAY
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (requestCount == null) {
            requestCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Constructor for creating new tracking entries
    public UserRateLimitTrackingEntity(UUID userId, Instant windowStart, WindowType windowType) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.windowStart = windowStart;
        this.windowType = windowType;
        this.requestCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Constructor with initial request count
    public UserRateLimitTrackingEntity(UUID userId, Instant windowStart, WindowType windowType, int requestCount) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.windowStart = windowStart;
        this.windowType = windowType;
        this.requestCount = requestCount;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Helper method to increment request count
    public void incrementRequestCount() {
        this.requestCount++;
        this.updatedAt = Instant.now();
    }
}