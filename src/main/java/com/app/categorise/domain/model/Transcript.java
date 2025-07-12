package com.app.categorise.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Transcript {
    private UUID id;
    private String videoUrl;
    private String transcript;
    private String description;
    private String title;
    private double duration;
    private Instant uploadedAt;
    private String accountId;
    private String account;
    private String identifierId;
    private String identifier;
    private UUID categoryId;
    private UUID userId;
    private Instant createdAt;

    public Transcript(
        UUID id,
        String videoUrl,
        String transcript,
        String description,
        String title,
        double duration,
        Instant uploadedAt,
        String accountId,
        String account,
        String identifierId,
        String identifier,
        UUID categoryId,
        UUID userId,
        Instant createdAt
    ) {
        this.id = id;
        this.videoUrl = videoUrl;
        this.transcript = transcript;
        this.description = description;
        this.title = title;
        this.duration = duration;
        this.uploadedAt = uploadedAt;
        this.accountId = accountId;
        this.account = account;
        this.identifierId = identifierId;
        this.identifier = identifier;
        this.categoryId = categoryId;
        this.userId = userId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public boolean hasValidUrl() {
        return videoUrl != null && !videoUrl.isBlank();
    }

    public boolean isLongForm() {
        return duration > 180.0; // threshold for shortform content
    }

    public String displayName() {
        return title != null ? title : description;
    }

    // Getters only (no setters – immutable domain object)
    public UUID getId() { return id; }
    public String getVideoUrl() { return videoUrl; }
    public String getTranscript() { return transcript; }
    public String getDescription() { return description; }
    public String getTitle() { return title; }
    public double getDuration() { return duration; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getAccountId() { return accountId; }
    public String getAccount() { return account; }
    public String getIdentifierId() { return identifierId; }
    public String getIdentifier() { return identifier; }
    public UUID getCategoryId() { return categoryId; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}

