package com.app.categorise.data.repository;

import com.app.categorise.data.entity.TranscriptionJobEntity;

import java.util.Optional;

/**
 * Custom repository interface for TranscriptionJobEntity operations
 * that require programmatic transaction management.
 */
public interface CustomTranscriptionJobRepository {

    /**
     * Atomically claim the next eligible PENDING job for processing.
     * Uses SELECT FOR UPDATE SKIP LOCKED to safely support concurrent pollers.
     *
     * @return the claimed job (now in PROCESSING status), or empty if no eligible jobs
     */
    Optional<TranscriptionJobEntity> claimNextPending();
}
