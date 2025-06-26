package com.app.categorise.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * DTO for Transcript with aliases for categories.
 * This class is used to represent a transcript with its associated metadata,
 * including aliases for categories.
 * Not saved to db, only used in API response.
 */
@Data
public class TranscriptDtoWithAliases {
    private String id;
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
    private List<String> categories;  // These will be alias names, not canonical
    private Instant createdAt;
}