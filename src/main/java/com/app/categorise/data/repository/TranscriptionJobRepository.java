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
     * Check for an existing active job for the same user and video URL.
     * Used to prevent duplicate job submissions.
     *
     * @param userId   the user's ID
     * @param videoUrl the video URL
     * @param statuses list of active statuses to check (typically PENDING, PROCESSING)
     * @return matching job if one exists
     */
    Optional<TranscriptionJobEntity> findByUserIdAndVideoUrlAndStatusIn(UUID userId, String videoUrl, List<JobStatus> statuses);

    // --- User job listing (WP04: API) ---

    /**
     * Paginated list of all jobs for a user, ordered by pageable.
     */
    Page<TranscriptionJobEntity> findByUserId(UUID userId, Pageable pageable);

    /**
     * Paginated list of jobs for a user filtered by status.
     */
    @Query("SELECT j FROM TranscriptionJobEntity j WHERE j.userId = :userId AND j.status = :status")
    Page<TranscriptionJobEntity> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") JobStatus status, Pageable pageable);

    // --- Cleanup (WP03: cleanup service) ---

    /**
     * Delete completed jobs older than 7 days.
     *
     * @return number of jobs deleted
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM transcription_jobs WHERE status = 'COMPLETED' AND completed_at < NOW() - INTERVAL '7 days'", nativeQuery = true)
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
