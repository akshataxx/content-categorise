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
 * Sends silent notifications when transcription jobs complete.
 * Sends visible notifications when jobs fail.
 * Marks devices inactive when FCM reports invalid tokens.
 *
 * Registered as a bean via {@link com.app.categorise.config.NotificationConfig}.
 */
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final DeviceRepository deviceRepository;

    public NotificationServiceImpl(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public void notifyJobCompleted(UUID userId, UUID jobId, UUID transcriptId, String title) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized, skipping notification for job {}", jobId);
            return;
        }
        // Send silent notification immediately - iOS handles badge management
        sendSilentNotification(userId, jobId, transcriptId);
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

    private void sendSilentNotification(UUID userId, UUID jobId, UUID transcriptId) {
        List<DeviceEntity> devices = deviceRepository.findByUserIdAndActiveTrue(userId);
        if (devices.isEmpty()) {
            log.debug("No active devices for user {}, skipping notification", userId);
            return;
        }
        // Send SILENT notification - iOS will manage badge count locally
        // No visible notification (no title/body), only background data delivery
        for (DeviceEntity device : devices) {
            Message message = Message.builder()
                    .setToken(device.getFcmToken())
                    // NO .setNotification() - this makes it a silent notification
                    .putData("type", "TRANSCRIPT_COMPLETE")
                    .putData("jobId", jobId.toString())
                    .putData("transcriptId", transcriptId.toString())
                    .putData("silent", "true")
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setContentAvailable(true)  // Wakes app in background
                                    .build())
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
                    .putData("type", "TRANSCRIPT_FAILED")
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
