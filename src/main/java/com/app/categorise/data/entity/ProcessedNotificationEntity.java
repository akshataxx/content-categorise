package com.app.categorise.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks processed webhook notifications to ensure idempotent handling.
 * Prevents duplicate processing of the same notification from Apple or Google.
 */
@Entity
@Table(name = "processed_notifications")
public class ProcessedNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notification_id", nullable = false, unique = true)
    private String notificationId;

    @Column(name = "notification_type")
    private String notificationType;

    @Column(name = "source", nullable = false)
    private String source; // APP_STORE, GOOGLE_PLAY

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedNotificationEntity() {}

    public ProcessedNotificationEntity(String notificationId, String notificationType, String source) {
        this.notificationId = notificationId;
        this.notificationType = notificationType;
        this.source = source;
        this.processedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
