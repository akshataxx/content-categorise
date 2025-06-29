package com.app.categorise.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for Transcript with aliases for categories.
 * This class is used to represent a transcript with its associated metadata,
 * including aliases for categories.
 * Not saved to db, only used in API response.
 */
public record TranscriptDtoWithAliases(
    UUID id,
    String videoUrl,
    String transcript,
    String description,
    String title,
    Double duration,
    Instant uploadedAt,
    String accountId,
    String account,
    String identifierId,
    String identifier,
    /** The final, user-visible alias for the transcript (e.g., "Big-Back", "Tech-Tok"). */
    String alias,
    /** The special, predefined categoryId */
    UUID categoryId,
    /** The special, predefined category name (e.g., "Recipe"). */
    String category,
    Instant createdAt
) {}
