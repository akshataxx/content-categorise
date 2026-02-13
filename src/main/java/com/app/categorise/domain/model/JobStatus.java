package com.app.categorise.domain.model;

/**
 * JobStatus - Strongly-typed status values for the transcription job lifecycle.
 * Values match the CHECK constraint in the transcription_jobs table.
 * Stored as VARCHAR string in the database via @Enumerated(EnumType.STRING).
 */
public enum JobStatus {
    PENDING,      // Waiting to be picked up by poller
    PROCESSING,   // Currently being transcribed
    COMPLETED,    // Transcript created successfully
    FAILED        // Permanently failed after exhausting retries
}
