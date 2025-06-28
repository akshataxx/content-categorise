package com.app.categorise.domain.model;

import java.time.Instant;
import java.util.List;

public class Transcript {
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
    private List<String> categories;
    private Instant createdAt = Instant.now();

    // Business logic methods
    public boolean hasValidUrl(){
        return videoUrl != null && !videoUrl.isEmpty();
    }
}
