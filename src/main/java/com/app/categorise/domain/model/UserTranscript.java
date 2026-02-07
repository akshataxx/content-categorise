package com.app.categorise.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * UserTranscript - User-specific association with a base transcript
 * Links a user to a transcript with user-specific categorization and access tracking
 */
public class UserTranscript {
    private UUID id;
    private UUID userId;
    private UUID baseTranscriptId;
    private UUID categoryId;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private String notes;

    public UserTranscript() {}

    public UserTranscript(UUID id, UUID userId, UUID baseTranscriptId, UUID categoryId, 
                         Instant createdAt, Instant lastAccessedAt) {
        this.id = id;
        this.userId = userId;
        this.baseTranscriptId = baseTranscriptId;
        this.categoryId = categoryId;
        this.createdAt = createdAt;
        this.lastAccessedAt = lastAccessedAt;
    }

    public UserTranscript(UUID id, UUID userId, UUID baseTranscriptId, UUID categoryId, 
                         Instant createdAt, Instant lastAccessedAt, String notes) {
        this(id, userId, baseTranscriptId, categoryId, createdAt, lastAccessedAt);
        this.notes = notes;
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

    public UUID getBaseTranscriptId() {
        return baseTranscriptId;
    }

    public void setBaseTranscriptId(UUID baseTranscriptId) {
        this.baseTranscriptId = baseTranscriptId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "UserTranscript{" +
                "id=" + id +
                ", userId=" + userId +
                ", baseTranscriptId=" + baseTranscriptId +
                ", categoryId=" + categoryId +
                ", createdAt=" + createdAt +
                ", lastAccessedAt=" + lastAccessedAt +
                '}';
    }
}