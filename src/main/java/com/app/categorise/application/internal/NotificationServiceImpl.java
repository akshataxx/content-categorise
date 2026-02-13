package com.app.categorise.application.internal;

import com.app.categorise.data.entity.DeviceEntity;
import com.app.categorise.data.repository.DeviceRepository;
import com.app.categorise.domain.service.NotificationService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

/**
 * FCM push notification implementation.
 * Sends notifications when transcription jobs complete or fail.
 * Batches when 3+ jobs complete for the same user within 10 seconds.
 * Marks devices inactive when FCM reports invalid tokens.
 */
@Service
@ConditionalOnBean(FirebaseApp.class)
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final int BATCH_THRESHOLD = 3;
    private static final long BATCH_DELAY_SECONDS = 10;

    private final DeviceRepository deviceRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "notification-batch"));

    /** userId -> pending batch. Guarded by synchronizing on this map. */
    private final Map<UUID, PendingBatch> pendingBatches = new ConcurrentHashMap<>();

    public NotificationServiceImpl(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public void notifyJobCompleted(UUID userId, UUID jobId, UUID transcriptId, String title) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized, skipping notification for job {}", jobId);
            return;
        }
        String safeTitle = title != null && !title.isBlank() ? title : "your video";
        enqueueOrSendCompleted(userId, jobId, transcriptId, safeTitle);
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

    private void enqueueOrSendCompleted(UUID userId, UUID jobId, UUID transcriptId, String title) {
        synchronized (pendingBatches) {
            PendingBatch batch = pendingBatches.get(userId);
            if (batch == null) {
                batch = new PendingBatch();
                batch.jobs.add(new CompletedJob(jobId, transcriptId, title));
                pendingBatches.put(userId, batch);
                batch.future = scheduler.schedule(
                        () -> flushBatch(userId),
                        BATCH_DELAY_SECONDS,
                        TimeUnit.SECONDS);
            } else {
                batch.jobs.add(new CompletedJob(jobId, transcriptId, title));
                batch.future.cancel(false);
                batch.future = scheduler.schedule(
                        () -> flushBatch(userId),
                        BATCH_DELAY_SECONDS,
                        TimeUnit.SECONDS);
            }
        }
    }

    private void flushBatch(UUID userId) {
        List<CompletedJob> jobs;
        synchronized (pendingBatches) {
            PendingBatch batch = pendingBatches.remove(userId);
            jobs = batch != null ? batch.jobs : List.of();
        }
        if (jobs.isEmpty()) return;

        List<DeviceEntity> devices = deviceRepository.findByUserIdAndActiveTrue(userId);
        if (devices.isEmpty()) {
            log.debug("No active devices for user {}, skipping notification", userId);
            return;
        }

        if (jobs.size() >= BATCH_THRESHOLD) {
            sendBatchNotification(userId, jobs, devices);
        } else {
            for (CompletedJob job : jobs) {
                sendReadyNotification(userId, job.jobId, job.transcriptId, job.title, devices);
            }
        }
    }

    private void sendReadyNotification(UUID userId, UUID jobId, UUID transcriptId, String title,
                                       List<DeviceEntity> devices) {
        String body = "Your transcript of \"" + title + "\" is ready";
        for (DeviceEntity device : devices) {
            Message message = Message.builder()
                    .setToken(device.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle("Transcript Ready")
                            .setBody(body)
                            .build())
                    .putData("type", "TRANSCRIPT_READY")
                    .putData("jobId", jobId.toString())
                    .putData("transcriptId", transcriptId.toString())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").setBadge(1).build())
                            .build())
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                    .build();
            sendToDevice(device, message);
        }
    }

    private void sendBatchNotification(UUID userId, List<CompletedJob> jobs, List<DeviceEntity> devices) {
        int count = jobs.size();
        String body = count + " transcripts are ready to view";
        StringBuilder jobIdsBuilder = new StringBuilder();
        for (int i = 0; i < jobs.size(); i++) {
            if (i > 0) jobIdsBuilder.append(",");
            jobIdsBuilder.append(jobs.get(i).jobId);
        }

        for (DeviceEntity device : devices) {
            Message message = Message.builder()
                    .setToken(device.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle("Transcripts Ready")
                            .setBody(body)
                            .build())
                    .putData("type", "BATCH_COMPLETE")
                    .putData("count", String.valueOf(count))
                    .putData("jobIds", jobIdsBuilder.toString())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").setBadge(count).build())
                            .build())
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
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

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(15, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class PendingBatch {
        final List<CompletedJob> jobs = new ArrayList<>();
        ScheduledFuture<?> future;
    }

    private static class CompletedJob {
        final UUID jobId;
        final UUID transcriptId;
        final String title;

        CompletedJob(UUID jobId, UUID transcriptId, String title) {
            this.jobId = jobId;
            this.transcriptId = transcriptId;
            this.title = title;
        }
    }
}
