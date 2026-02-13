package com.app.categorise.data.repository;

import com.app.categorise.data.entity.TranscriptionJobEntity;
import com.app.categorise.domain.model.JobStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Custom repository implementation for TranscriptionJobEntity.
 * Handles the claimNextPending operation which requires SELECT FOR UPDATE SKIP LOCKED
 * followed by a status update -- not expressible as a simple Spring Data @Query.
 */
@Repository
public class TranscriptionJobRepositoryImpl implements CustomTranscriptionJobRepository {

    private final EntityManager entityManager;

    public TranscriptionJobRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Optional<TranscriptionJobEntity> claimNextPending() {
        // Step 1: SELECT the next eligible PENDING job with row-level lock
        @SuppressWarnings("unchecked")
        List<TranscriptionJobEntity> results = entityManager.createNativeQuery(
                """
                SELECT * FROM transcription_jobs
                WHERE status = 'PENDING'
                  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
                ORDER BY updated_at ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """,
                TranscriptionJobEntity.class
        ).getResultList();

        if (results.isEmpty()) {
            return Optional.empty();
        }

        // Step 2: Update the claimed job to PROCESSING
        TranscriptionJobEntity job = results.get(0);
        job.setStatus(JobStatus.PROCESSING);
        job.setUpdatedAt(Instant.now());
        entityManager.merge(job);

        return Optional.of(job);
    }
}
