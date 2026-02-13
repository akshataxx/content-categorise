package com.app.categorise.domain.service;

import java.util.UUID;

/**
 * Service for sending FCM push notifications when transcription jobs complete or fail.
 * Implementations may be no-op when Firebase is not configured (e.g. dev/test).
 */
public interface NotificationService {

    /**
     * Notify user that a transcription job completed successfully.
     *
     * @param userId       the user who submitted the job
     * @param jobId        the transcription job ID
     * @param transcriptId the created transcript ID (for deep linking)
     * @param title        the transcript/video title for the notification body
     */
    void notifyJobCompleted(UUID userId, UUID jobId, UUID transcriptId, String title);

    /**
     * Notify user that a transcription job failed permanently.
     *
     * @param userId      the user who submitted the job
     * @param jobId       the transcription job ID
     * @param errorMessage brief error reason for the notification body
     */
    void notifyJobFailed(UUID userId, UUID jobId, String errorMessage);
}
