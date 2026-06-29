package com.app.categorise.application.internal;

import com.app.categorise.data.entity.DeviceEntity;
import com.app.categorise.data.repository.DeviceRepository;
import com.app.categorise.domain.service.NotificationService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.List;

/**
 * FCM push notification implementation.
 * Sends a visible notification when transcription jobs complete.
 * Sends visible notifications when jobs fail.
 * Marks devices inactive when FCM reports invalid tokens.
 *
 * Registered as a bean via {@link com.app.categorise.config.NotificationConfig}.
 */
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final DeviceRepository deviceRepository;

    /** Canonical notification type strings — must match the iOS NotificationType enum. */
    static final String TYPE_TRANSCRIPT_COMPLETE = "TRANSCRIPT_COMPLETE";
    static final String TYPE_TRANSCRIPT_FAILED = "TRANSCRIPT_FAILED";
    private static final String DEFAULT_TITLE = "your video";

    public NotificationServiceImpl(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public void notifyJobCompleted(UUID userId, UUID jobId, UUID transcriptId, String title) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized, skipping notification for job {}", jobId);
            return;
        }
        // Single VISIBLE push carrying data so the user is informed AND the UI can deep-link/refresh.
        sendCompletionNotification(userId, jobId, transcriptId, title);
    }

    @Override
    public void notifyJobFailed(UUID userId, UUID jobId, String errorMessage) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized, skipping notification for failed job {}", jobId);
            return;
        }
        // Failures are sent immediately (no batching)
        sendFailedNotification(userId, jobId, errorMessage != null ? errorMessage : "Unknown error");
    }

    private void sendCompletionNotification(UUID userId, UUID jobId, UUID transcriptId, String title) {
        List<DeviceEntity> devices = deviceRepository.findByUserIdAndActiveTrue(userId);
        if (devices.isEmpty()) {
            log.debug("No active devices for user {}, skipping notification", userId);
            return;
        }

        String safeTitle = (title != null && !title.isBlank()) ? title : DEFAULT_TITLE;
        String body = "\"" + safeTitle + "\" is ready to view";

        // SINGLE visible push: notification block (user-facing) + data (deep-link / UI refresh).
        for (DeviceEntity device : devices) {
            Message message = Message.builder()
                    .setToken(device.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle("Transcript ready")
                            .setBody(body)
                            .build())
                    .putData("type", TYPE_TRANSCRIPT_COMPLETE)
                    .putData("jobId", jobId.toString())
                    .putData("transcriptId", transcriptId.toString())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").build())
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();
            sendToDevice(device, message);
        }
    }


    private void sendFailedNotification(UUID userId, UUID jobId, String errorMessage) {
        List<DeviceEntity> devices = deviceRepository.findByUserIdAndActiveTrue(userId);
        if (devices.isEmpty()) {
            log.debug("No active devices for user {}, skipping failed notification", userId);
            return;
        }

        String body = "Could not transcribe video: " + truncateError(errorMessage, 100);
        for (DeviceEntity device : devices) {
            Message message = Message.builder()
                    .setToken(device.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle("Transcription Failed")
                            .setBody(body)
                            .build())
                    .putData("type", TYPE_TRANSCRIPT_FAILED)
                    .putData("jobId", jobId.toString())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").build())
                            .build())
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                    .build();
            sendToDevice(device, message);
        }
    }

    private void sendToDevice(DeviceEntity device, Message message) {
        try {
            var future = FirebaseMessaging.getInstance().sendAsync(message);
            future.addListener(() -> {
                try {
                    future.get();
                } catch (Exception e) {
                    handleSendError(device, e);
                }
            }, Runnable::run);
        } catch (Exception e) {
            handleSendError(device, e);
        }
    }

    private void handleSendError(DeviceEntity device, Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof FirebaseMessagingException fme) {
            String code = fme.getErrorCode() != null ? fme.getErrorCode().name() : "";
            if ("UNREGISTERED".equals(code) || "INVALID_ARGUMENT".equals(code)) {
                log.info("Marking device {} inactive (invalid token: {})", device.getId(), code);
                device.setActive(false);
                deviceRepository.save(device);
                return;
            }
        }
        log.warn("Failed to send notification to device {}: {}", device.getId(), e.getMessage());
    }

    private static String truncateError(String msg, int maxLen) {
        if (msg == null) return "Unknown error";
        return msg.length() <= maxLen ? msg : msg.substring(0, maxLen) + "...";
    }

}
