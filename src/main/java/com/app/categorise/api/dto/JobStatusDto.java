package com.app.categorise.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for individual transcription job status.
 * Matches the JobStatusDto schema in contracts/jobs-api.yaml.
 *
 * Serialization notes:
 * - Instant fields serialize as ISO 8601 strings via Jackson's JavaTimeModule (configured in AppConfig)
 * - errorMessage, baseTranscriptId are nullable (Jackson serializes as null in JSON)
 * - status is a String (not enum) for flexibility
 */
public record JobStatusDto(
    UUID id,
    String videoUrl,
    String status,
    String errorMessage,
    int retryCount,
    Instant updatedAt,
    UUID baseTranscriptId,
    UUID userTranscriptId
) {}
