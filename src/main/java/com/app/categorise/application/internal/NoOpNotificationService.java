package com.app.categorise.application.internal;

import com.app.categorise.domain.service.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * No-op NotificationService for when Firebase is not configured (dev/test).
 * Allows the app to start and run without FCM.
 */
@Service
@ConditionalOnMissingBean(NotificationService.class)
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
