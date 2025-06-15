package com.app.categorise.entity;


import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * represents the transcript of a video
 */
@Document(collection = "transcripts")
public class Transcript {

    @MongoId
    private String id;
    private String videoUrl;
    private String transcript;
    private String description;
    private List<String> categories;
    private LocalDateTime createdAt = LocalDateTime.now();

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
    public List<String> getCategories() { return categories; }
    public LocalDateTime getCreatedAt() { return createdAt; }

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
    public void setCategories(List<String> categories) {
        this.categories = categories;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
