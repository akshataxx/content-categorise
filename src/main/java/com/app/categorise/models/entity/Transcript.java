package com.app.categorise.models.entity;


import lombok.ToString;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * represents the transcript of a video
 */
@ToString
@Document(collection = "transcripts")
public class Transcript {
    @MongoId
    private String id;
    private String videoUrl;
    private String transcript;
    private String description;
    private String title;
    private int duration;
    private Instant uploadedAt;
    private String accountId;
    private String account;
    private String identifierId;
    private String identifier;
    private List<String> categories;
    private Instant createdAt = Instant.now();

    public String getId() {
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
    public int getDuration() { return duration; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getAccountId() { return accountId; }
    public String getAccount() { return account; }
    public String getIdentifierId() { return identifierId; }
    public String getIdentifier() { return identifier; }
    public List<String> getCategories() { return categories; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(String id) {
        this.id = id;
    }
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
    public void setDuration(int duration) {
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
    public void setCategories(List<String> categories) {
        this.categories = categories;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
