package com.app.categorise.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * UserTranscriptEntity - JPA entity for user_transcripts table
 * Links users to base transcripts with user-specific categorization and metadata
 */
@Entity
@Table(name = "user_transcripts")
public class UserTranscriptEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    private Instant createdAt;
    private Instant lastAccessedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Lazy-loaded relationship to base transcript
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_transcript_id")
    private BaseTranscriptEntity baseTranscript;

    // Lazy-loaded relationship to category
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    public UserTranscriptEntity() {}

    public UserTranscriptEntity(UUID userId, BaseTranscriptEntity baseTranscript, CategoryEntity category) {
        this.userId = userId;
        this.baseTranscript = baseTranscript;
        this.category = category;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastAccessedAt == null) {
            lastAccessedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastAccessedAt = Instant.now();
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
        return baseTranscript != null ? baseTranscript.getId() : null;
    }

    public UUID getCategoryId() {
        return category != null ? category.getId() : null;
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

    public BaseTranscriptEntity getBaseTranscript() {
        return baseTranscript;
    }

    public void setBaseTranscript(BaseTranscriptEntity baseTranscript) {
        this.baseTranscript = baseTranscript;
    }

    public CategoryEntity getCategory() {
        return category;
    }

    public void setCategory(CategoryEntity category) {
        this.category = category;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "UserTranscriptEntity{" +
                "id=" + id +
                ", userId=" + userId +
                ", baseTranscriptId=" + getBaseTranscriptId() +
                ", categoryId=" + getCategoryId() +
                ", createdAt=" + createdAt +
                ", lastAccessedAt=" + lastAccessedAt +
                '}';
    }
}