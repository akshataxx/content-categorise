package com.app.categorise.application.internal;

import com.app.categorise.domain.service.NotificationService;

import java.util.UUID;

/**
 * No-op NotificationService for when Firebase is not configured (dev/test).
 * Allows the app to start and run without FCM.
 *
 * Registered as a bean via {@link com.app.categorise.config.NotificationConfig}.
 */
public class NoOpNotificationService implements NotificationService {

    @Override
    public void notifyJobCompleted(UUID userId, UUID jobId, UUID transcriptId, String title) {
        // No-op
    }

    @Override
    public void notifyJobFailed(UUID userId, UUID jobId, String errorMessage) {
        // No-op
    }
}
