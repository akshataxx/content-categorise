package com.app.categorise.domain.service;

import com.app.categorise.data.repository.TranscriptionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * JobCleanupService - Daily scheduled cleanup of old transcription jobs.
 *
 * Prevents the transcription_jobs table from growing indefinitely by removing:
 * - COMPLETED jobs older than 7 days (based on completed_at)
 * - FAILED jobs older than 30 days (based on updated_at)
 *
 * PENDING and PROCESSING jobs are never deleted.
 * Runs daily at 3:00 AM server time (australia-southeast1 / AEST).
 */
@Service
public class JobCleanupService {

    private static final Logger log = LoggerFactory.getLogger(JobCleanupService.class);

    private final TranscriptionJobRepository jobRepository;

    public JobCleanupService(TranscriptionJobRepository jobRepository) {
        this.jobRepository = jobRepository;
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
}
