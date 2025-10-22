package com.app.categorise.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * BaseTranscriptEntity - JPA entity for base_transcripts table
 * Stores core transcript data independent of users to prevent duplicate transcriptions
 */
@Entity
@Table(name = "base_transcripts")
public class BaseTranscriptEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, columnDefinition = "TEXT")
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(columnDefinition = "JSONB")
    private String structuredContent;

    private String description;
    private String title;
    private Double duration;
    private Instant uploadedAt;
    private String accountId;
    private String account;
    private String identifierId;
    private String identifier;
    private Instant createdAt;
    private Instant transcribedAt;

    public BaseTranscriptEntity() {}

    public BaseTranscriptEntity(String videoUrl, String transcript, String structuredContent,
                               String description, String title, Double duration, Instant uploadedAt,
                               String accountId, String account, String identifierId,
                               String identifier) {
        this.videoUrl = videoUrl;
        this.transcript = transcript;
        this.structuredContent = structuredContent;
        this.description = description;
        this.title = title;
        this.duration = duration;
        this.uploadedAt = uploadedAt;
        this.accountId = accountId;
        this.account = account;
        this.identifierId = identifierId;
        this.identifier = identifier;
        this.createdAt = Instant.now();
        this.transcribedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (transcribedAt == null) {
            transcribedAt = Instant.now();
        }
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getStructuredContent() {
        return structuredContent;
    }

    public void setStructuredContent(String structuredContent) {
        this.structuredContent = structuredContent;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getIdentifierId() {
        return identifierId;
    }

    public void setIdentifierId(String identifierId) {
        this.identifierId = identifierId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getTranscribedAt() {
        return transcribedAt;
    }

    public void setTranscribedAt(Instant transcribedAt) {
        this.transcribedAt = transcribedAt;
    }

    @Override
    public String toString() {
        return "BaseTranscriptEntity{" +
                "id=" + id +
                ", videoUrl='" + videoUrl + '\'' +
                ", title='" + title + '\'' +
                ", duration=" + duration +
                ", createdAt=" + createdAt +
                '}';
    }
}