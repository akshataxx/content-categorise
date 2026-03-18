package com.app.categorise.data.entity;

import com.app.categorise.domain.model.JobStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * TranscriptionJobEntity - JPA entity for the transcription_jobs table.
 * Durable record of every transcription request, replacing the in-memory ThreadPool queue.
 */
@Entity
@Table(name = "transcription_jobs")
public class TranscriptionJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "video_url", nullable = false, length = 2048)
    private String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "base_transcript_id")
    private UUID baseTranscriptId;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_transcript_id", insertable = false, updatable = false)
    private UserTranscriptEntity userTranscript;

    @Column(name = "user_transcript_id")
    private UUID userTranscriptId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TranscriptionJobEntity() {}

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public UUID getBaseTranscriptId() {
        return baseTranscriptId;
    }

    public void setBaseTranscriptId(UUID baseTranscriptId) {
        this.baseTranscriptId = baseTranscriptId;
    }

    public UserTranscriptEntity getUserTranscript() {
        return userTranscript;
    }

    public UUID getUserTranscriptId() {
        return userTranscriptId;
    }

    public void setUserTranscriptId(UUID userTranscriptId) {
        this.userTranscriptId = userTranscriptId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "TranscriptionJobEntity{" +
                "id=" + id +
                ", userId=" + userId +
                ", videoUrl='" + videoUrl + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
