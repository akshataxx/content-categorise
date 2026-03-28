package com.app.categorise.data.repository;

import com.app.categorise.data.entity.TranscriptionJobEntity;
import com.app.categorise.domain.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TranscriptionJobRepository - Data access for transcription_jobs table.
 *
 * Provides queries for:
 * - Crash recovery (resetProcessingToPending)
 * - Deduplication check (findByUserIdAndVideoUrlAndStatusIn)
 * - User job listing with pagination (findByUserId, findByUserIdAndStatus)
 * - Cleanup of old completed/failed jobs
 *
 * The claimNextPending() method is provided via CustomTranscriptionJobRepository
 * using a programmatic SELECT FOR UPDATE SKIP LOCKED approach.
 */
@Repository
public interface TranscriptionJobRepository extends JpaRepository<TranscriptionJobEntity, UUID>, CustomTranscriptionJobRepository {

    // --- Crash recovery (WP03: poller startup) ---

    /**
     * Reset all PROCESSING jobs back to PENDING on application startup.
     * These are jobs that were in-flight when the previous instance crashed.
     *
     * @return number of jobs reset
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE transcription_jobs SET status = 'PENDING', updated_at = NOW() WHERE status = 'PROCESSING'", nativeQuery = true)
    int resetProcessingToPending();

    // --- Deduplication (WP02: job service) ---

    /**
     * Find the most recent job for a given user and video URL, regardless of status.
     * Used by createOrGetExisting to decide whether to reuse, retry, or create a new job.
     *
     * @param userId   the user's ID
     * @param videoUrl the video URL
     * @return the latest job if one exists
     */
    Optional<TranscriptionJobEntity> findTopByUserIdAndVideoUrlOrderByUpdatedAtDesc(UUID userId, String videoUrl);

    // --- User job listing (WP04: API) ---

    /**
     * Paginated list of jobs for a user with optional status and date range filters.
     * Pass null for any filter to skip it.
     */
    @Query("SELECT j FROM TranscriptionJobEntity j WHERE j.userId = :userId" +
           " AND (:status IS NULL OR j.status = :status)" +
           " AND (CAST(:from AS timestamp) IS NULL OR j.updatedAt >= :from)" +
           " AND (CAST(:to AS timestamp) IS NULL OR j.updatedAt <= :to)")
    Page<TranscriptionJobEntity> findByUserIdFiltered(
            @Param("userId") UUID userId,
            @Param("status") JobStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    // --- Cleanup (WP03: cleanup service) ---

    /**
     * Delete completed jobs older than 7 days.
     *
     * @return number of jobs deleted
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM transcription_jobs WHERE status = 'COMPLETED' AND updated_at < NOW() - INTERVAL '7 days'", nativeQuery = true)
    int deleteOldCompletedJobs();

    /**
     * Delete failed jobs older than 30 days.
     *
     * @return number of jobs deleted
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM transcription_jobs WHERE status = 'FAILED' AND updated_at < NOW() - INTERVAL '30 days'", nativeQuery = true)
    int deleteOldFailedJobs();
}
