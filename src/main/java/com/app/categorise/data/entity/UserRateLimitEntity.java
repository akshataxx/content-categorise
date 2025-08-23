package com.app.categorise.data.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * UserRateLimitEntity - JPA entity for user_rate_limits table
 * Stores rate limit configurations per user
 */
@Entity
@Table(name = "user_rate_limits")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRateLimitEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "transcripts_per_minute_limit", nullable = false)
    private Integer transcriptsPerMinuteLimit;

    @Column(name = "transcripts_per_day_limit", nullable = false)
    private Integer transcriptsPerDayLimit;

    @Column(name = "total_transcripts_limit", nullable = false)
    private Integer totalTranscriptsLimit;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Constructor for creating new entities with default values
    public UserRateLimitEntity(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.transcriptsPerMinuteLimit = 5;
        this.transcriptsPerDayLimit = 100;
        this.totalTranscriptsLimit = 10000;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Constructor for creating entities with custom limits
    public UserRateLimitEntity(UUID userId, int transcriptsPerMinuteLimit, 
                              int transcriptsPerDayLimit, int totalTranscriptsLimit) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.transcriptsPerMinuteLimit = transcriptsPerMinuteLimit;
        this.transcriptsPerDayLimit = transcriptsPerDayLimit;
        this.totalTranscriptsLimit = totalTranscriptsLimit;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}