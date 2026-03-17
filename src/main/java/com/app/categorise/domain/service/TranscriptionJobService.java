package com.app.categorise.domain.service;

import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.TranscriptionJobEntity;
import com.app.categorise.data.repository.BaseTranscriptRepository;
import com.app.categorise.data.repository.TranscriptionJobRepository;
import com.app.categorise.domain.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TranscriptionJobService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionJobService.class);

    /** Max retries before marking a job as FAILED. Configured in code (not per-row in DB). */
    private static final int MAX_RETRIES = 3;

    private final TranscriptionJobRepository jobRepository;
    private final BaseTranscriptRepository baseTranscriptRepository;
    private final NotificationService notificationService;

    public TranscriptionJobService(TranscriptionJobRepository jobRepository,
                                   BaseTranscriptRepository baseTranscriptRepository,
                                   NotificationService notificationService) {
        this.jobRepository = jobRepository;
        this.baseTranscriptRepository = baseTranscriptRepository;
        this.notificationService = notificationService;
    }

    // --- Job creation + deduplication ---

    @Transactional
    public TranscriptionJobEntity createOrGetExisting(UUID userId, String videoUrl) {
        // 1. Check for existing active job (PENDING or PROCESSING)
        Optional<TranscriptionJobEntity> existing = jobRepository
                .findByUserIdAndVideoUrlAndStatusIn(userId, videoUrl, List.of(JobStatus.PENDING, JobStatus.PROCESSING));
        if (existing.isPresent()) {
            TranscriptionJobEntity existingJob = existing.get();
            log.info("Returning existing job {} (status={}) for user={} videoUrl={}",
                    existingJob.getId(), existingJob.getStatus(), userId, videoUrl);
            return existingJob;
        }

        // 2. Check if transcript already exists (another user already transcribed this URL)
        Optional<BaseTranscriptEntity> existingTranscript = baseTranscriptRepository.findByVideoUrl(videoUrl);
        if (existingTranscript.isPresent()) {
            log.info("Transcript already exists for videoUrl={}, creating COMPLETED job", videoUrl);
            TranscriptionJobEntity job = new TranscriptionJobEntity();
            job.setUserId(userId);
            job.setVideoUrl(videoUrl);
            job.setStatus(JobStatus.COMPLETED);
            job.setBaseTranscriptId(existingTranscript.get().getId());
            return jobRepository.save(job);
        }

        // 3. Create new PENDING job
        TranscriptionJobEntity job = new TranscriptionJobEntity();
        job.setUserId(userId);
        job.setVideoUrl(videoUrl);
        job.setStatus(JobStatus.PENDING);
        job = jobRepository.save(job);
        log.info("Job {} created for user={} videoUrl={}", job.getId(), userId, videoUrl);
        return job;
    }

    // --- State transitions ---

    @Transactional
    public void transitionToProcessing(TranscriptionJobEntity job) {
        job.setStatus(JobStatus.PROCESSING);
        jobRepository.save(job);
    }

    @Transactional
    public void markCompleted(TranscriptionJobEntity job, UUID baseTranscriptId, UUID userTranscriptId) {
        job.setStatus(JobStatus.COMPLETED);
        job.setBaseTranscriptId(baseTranscriptId);
        job.setUserTranscriptId(userTranscriptId);
        jobRepository.save(job);
        if (baseTranscriptId != null) {
            try {
                String title = baseTranscriptRepository.findById(baseTranscriptId)
                        .map(BaseTranscriptEntity::getTitle)
                        .filter(t -> t != null && !t.isBlank())
                        .orElse("your video");
                notificationService.notifyJobCompleted(job.getUserId(), job.getId(), baseTranscriptId, title);
            } catch (Exception e) {
                log.warn("Failed to send notification for job {}: {}", job.getId(), e.getMessage());
            }
        }
    }

    /**
     * Convenience method: marks job completed by looking up the base transcript from the video URL.
     * Used by the sync endpoint after the pipeline finishes.
     */
    @Transactional
    public void markCompletedForUrl(TranscriptionJobEntity job, String videoUrl, UUID userTranscriptId) {
        UUID baseTranscriptId = baseTranscriptRepository.findByVideoUrl(videoUrl)
                .map(BaseTranscriptEntity::getId)
                .orElse(null);
        markCompleted(job, baseTranscriptId, userTranscriptId);
    }

    // --- Failure handling ---

    @Transactional
    public void handleFailure(TranscriptionJobEntity job, Exception ex) {
        if (isTransientFailure(ex) && job.getRetryCount() < MAX_RETRIES) {
            // Re-queue with exponential backoff: 5s, 25s, 125s
            job.setRetryCount(job.getRetryCount() + 1);
            long backoffSeconds = (long) Math.pow(5, job.getRetryCount());
            job.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
            job.setStatus(JobStatus.PENDING);
            job.setErrorMessage(ex.getMessage());
            log.warn("Job {} failed (attempt {}/{}), retrying in {}s: {}",
                    job.getId(), job.getRetryCount(), MAX_RETRIES, backoffSeconds, ex.getMessage());
        } else {
            // Permanent failure
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            log.error("Job {} permanently failed: {}", job.getId(), ex.getMessage());
            try {
                notificationService.notifyJobFailed(job.getUserId(), job.getId(), ex.getMessage());
            } catch (Exception ne) {
                log.warn("Failed to send failure notification for job {}: {}", job.getId(), ne.getMessage());
            }
        }
        jobRepository.save(job);
    }

    private boolean isTransientFailure(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        // Permanent failures - do not retry
        if (msg.contains("unsupported url") || msg.contains("is not a valid url")) return false;
        if (msg.contains("private video") || msg.contains("login required")) return false;
        if (msg.contains("no space left on device")) return false;
        // Transient failures - retry
        if (ex instanceof java.net.SocketTimeoutException) return true;
        if (ex instanceof java.net.ConnectException) return true;
        if (msg.contains("429") || msg.contains("rate limit")) return true;
        if (msg.contains("500") || msg.contains("502") || msg.contains("503")) return true;
        if (msg.contains("timeout")) return true;
        // Default: treat as transient (safer to retry than to lose)
        return true;
    }

    // --- Crash recovery (on startup) ---

    /**
     * On application startup, reset any PROCESSING jobs back to PENDING.
     * These are jobs that were in-flight when the previous instance crashed or restarted.
     * Safe because the transcription pipeline is idempotent (dedup in VideoService).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStuckJobs() {
        int recovered = jobRepository.resetProcessingToPending();
        if (recovered > 0) {
            log.info("Recovered {} stuck jobs after restart", recovered);
        } else {
            log.info("No stuck jobs to recover on startup");
        }
    }
}
