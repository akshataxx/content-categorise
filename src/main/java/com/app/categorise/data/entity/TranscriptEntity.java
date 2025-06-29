package com.app.categorise.data.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;


/**
 * represents the transcript of a video
 */
@Entity
@Table(name = "transcripts")
@Data
public class TranscriptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Information about the video
    @Column(columnDefinition = "TEXT")
    private String videoUrl;
    @Column(columnDefinition = "TEXT")
    private String transcript;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(columnDefinition = "TEXT")
    private String title;
    private double duration;
    private Instant uploadedAt;

    // Identifying information about the transcript and uploader
    private String accountId;
    private String account;
    @Column(length = 1024)
    private String identifierId;
    private String identifier;

    private UUID categoryId;

    private UUID userId;
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }
    public String getVideoUrl() {
        return videoUrl;
    }
    public String getTranscript() {
        return transcript;
    }
    public  String getDescription() { return description; }
    public String getTitle() { return title; }
    public double getDuration() { return duration; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getAccountId() { return accountId; }
    public String getAccount() { return account; }
    public String getIdentifierId() { return identifierId; }
    public String getIdentifier() { return identifier; }
    public UUID getCategoryId() { return categoryId; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getUserId() { return userId; }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public void setDuration(double duration) {
        this.duration = duration;
    }
    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    public void setAccount(String account) {
        this.account = account;
    }
    public void setIdentifierId(String identifierId) {
        this.identifierId = identifierId;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
