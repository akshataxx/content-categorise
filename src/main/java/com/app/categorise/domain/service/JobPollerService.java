package com.app.categorise.domain.service;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.TranscriptionJobEntity;
import com.app.categorise.data.repository.TranscriptionJobRepository;
import com.app.categorise.domain.model.RateLimitResult;
import com.app.categorise.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * JobPollerService - Background worker that polls the DB every 5 seconds
 * for PENDING transcription jobs and submits them for processing on the
 * media executor thread pool.
 *
 * Uses SELECT FOR UPDATE SKIP LOCKED (via claimNextPending) to safely support
 * concurrent pollers without double-processing.
 */
@Service
public class JobPollerService {

    private static final Logger log = LoggerFactory.getLogger(JobPollerService.class);

    private final TranscriptionJobRepository jobRepository;
    private final TranscriptionJobService jobService;
    private final VideoService videoService;
    private final RateLimitService rateLimitService;
    private final Executor mediaExecutor;

    public JobPollerService(TranscriptionJobRepository jobRepository,
                            TranscriptionJobService jobService,
                            VideoService videoService,
                            RateLimitService rateLimitService,
                            @Qualifier("mediaExecutor") Executor mediaExecutor) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.videoService = videoService;
        this.rateLimitService = rateLimitService;
        this.mediaExecutor = mediaExecutor;
    }

    /**
     * Poll for pending jobs every 5 seconds. Claims the next eligible job
     * (PENDING with next_retry_at <= now or null) and submits it for processing.
     * Silent when no jobs are available (no INFO logging on empty polls).
     */
    @Scheduled(fixedDelay = 5000)
    public void pollAndProcess() {
        Optional<TranscriptionJobEntity> claimed = jobRepository.claimNextPending();
        claimed.ifPresent(job -> {
            log.info("Claimed job {} for processing, videoUrl={}", job.getId(), LogSanitizer.sanitize(job.getVideoUrl()));
            mediaExecutor.execute(() -> executeJob(job));
        });
    }

    /**
     * Execute the transcription pipeline for a claimed job.
     * Runs on the media executor thread pool.
     */
    private void executeJob(TranscriptionJobEntity job) {
        try {
            // Re-check rate limits before processing to prevent quota bypass for queued jobs
            RateLimitResult rateLimitResult = rateLimitService.checkRateLimit(job.getUserId());
            if (!rateLimitResult.isAllowed()) {
                log.warn("Job {} rejected at processing time — user {} over rate limit ({})",
                        job.getId(), job.getUserId(), rateLimitResult.getLimitType());
                jobService.handleFailure(job,
                        new RuntimeException("Rate limit exceeded at processing time: " + rateLimitResult.getReason()));
                return;
            }

            // Run the existing transcription pipeline
            // processVideoAndCreateTranscript returns a CompletableFuture;
            // .join() blocks since we're already on the media executor thread
            TranscriptDtoWithAliases result = videoService.processVideoAndCreateTranscript(
                    job.getVideoUrl(), job.getUserId()
            ).join();

            // Mark completed with both baseTranscriptId (by URL lookup) and userTranscriptId
            jobService.markCompletedForUrl(job, job.getVideoUrl(), result.id());
            log.info("Job {} completed successfully, videoUrl={}", job.getId(), LogSanitizer.sanitize(job.getVideoUrl()));

        } catch (Exception ex) {
            log.error("Job {} failed: {}", job.getId(), ex.getMessage(), ex);
            // handleFailure classifies transient vs permanent failures and manages retry bounds
            jobService.handleFailure(job, ex);
        }
    }
}
