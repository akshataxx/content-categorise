package com.app.categorise.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * BaseTranscript - Core transcript without user-specific data
 * Represents a unique video transcript that can be shared across multiple users
 */
public class BaseTranscript {
    private UUID id;
    private String videoUrl;
    private String transcript;
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

    public BaseTranscript() {}

    public BaseTranscript(UUID id, String videoUrl, String transcript, String description, 
                         String title, Double duration, Instant uploadedAt, String accountId, 
                         String account, String identifierId, String identifier, 
                         Instant createdAt, Instant transcribedAt) {
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
        this.createdAt = createdAt;
        this.transcribedAt = transcribedAt;
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
        return "BaseTranscript{" +
                "id=" + id +
                ", videoUrl='" + videoUrl + '\'' +
                ", title='" + title + '\'' +
                ", duration=" + duration +
                ", createdAt=" + createdAt +
                '}';
    }
}