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
    /** The final, user-visible alias for the transcript (e.g., "Big-Back", "Tech-Tok"). */
    private String alias;
    /** The special, predefined category if applicable (e.g., "Recipe"). Can be null. */
    private String canonicalCategory;
    /** The internal key used for grouping ("Recipe", "tech"). Sent to the client for rename requests. */
    private String groupingKey;
    private Instant createdAt;
}