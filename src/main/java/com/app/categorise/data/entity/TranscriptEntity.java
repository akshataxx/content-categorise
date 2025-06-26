package com.app.categorise.data.entity;


import lombok.ToString;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

/**
 * represents the transcript of a video
 */
@ToString
@Document(collection = "transcripts")
public class TranscriptEntity {
    @MongoId
    private String id;
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
    private String alias;
    private String canonicalCategory;
    private String groupingKey;
    private String userId;
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
    public double getDuration() { return duration; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getAccountId() { return accountId; }
    public String getAccount() { return account; }
    public String getIdentifierId() { return identifierId; }
    public String getIdentifier() { return identifier; }
    public String getAlias() { return alias; }
    public String getCanonicalCategory() { return canonicalCategory; }
    public String getGroupingKey() { return groupingKey; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUserId() { return userId; }

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
    public void setAlias(String alias) { this.alias = alias; }
    public void setCanonicalCategory(String canonicalCategory) { this.canonicalCategory = canonicalCategory; }
    public void setGroupingKey(String groupingKey) { this.groupingKey = groupingKey; }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
}
