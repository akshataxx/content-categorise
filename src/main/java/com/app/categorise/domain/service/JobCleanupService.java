package com.app.categorise.domain.service;

import com.app.categorise.data.entity.UserRateLimitTrackingEntity;
import com.app.categorise.data.repository.TranscriptionJobRepository;
import com.app.categorise.data.repository.UserRateLimitTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * JobCleanupService - Daily scheduled cleanup of old data.
 *
 * Prevents tables from growing indefinitely by removing:
 * - COMPLETED transcription jobs older than 7 days
 * - FAILED transcription jobs older than 30 days
 * - MINUTE rate-limit tracking rows older than 2 hours
 * - DAY rate-limit tracking rows older than 2 days
 *
 * Runs daily at 3:00 AM server time (australia-southeast1 / AEST).
 */
@Service
public class JobCleanupService {

    private static final Logger log = LoggerFactory.getLogger(JobCleanupService.class);

    private final TranscriptionJobRepository jobRepository;
    private final UserRateLimitTrackingRepository trackingRepository;

    public JobCleanupService(TranscriptionJobRepository jobRepository,
                             UserRateLimitTrackingRepository trackingRepository) {
        this.jobRepository = jobRepository;
        this.trackingRepository = trackingRepository;
    }

    @Scheduled(cron = "0 0 3 * * *") // Daily at 3:00 AM
    public void cleanupOldJobs() {
        int completedDeleted = jobRepository.deleteOldCompletedJobs();
        int failedDeleted = jobRepository.deleteOldFailedJobs();

        if (completedDeleted > 0 || failedDeleted > 0) {
            log.info("Job cleanup: removed {} completed (>7d) and {} failed (>30d) jobs",
                    completedDeleted, failedDeleted);
        }
    }

    @Scheduled(cron = "0 30 3 * * *") // Daily at 3:30 AM
    @Transactional
    public void cleanupOldRateLimitTracking() {
        Instant minuteCutoff = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant dayCutoff = Instant.now().minus(2, ChronoUnit.DAYS);

        int minuteDeleted = trackingRepository.deleteOldTrackingRecords(
                UserRateLimitTrackingEntity.WindowType.MINUTE, minuteCutoff);
        int dayDeleted = trackingRepository.deleteOldTrackingRecords(
                UserRateLimitTrackingEntity.WindowType.DAY, dayCutoff);

        if (minuteDeleted > 0 || dayDeleted > 0) {
            log.info("Rate limit tracking cleanup: removed {} minute and {} day tracking rows",
                    minuteDeleted, dayDeleted);
        }
    }
}
