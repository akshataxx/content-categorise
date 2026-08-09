package com.app.categorise.domain.service;

import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.TranscriptionJobEntity;
import com.app.categorise.data.repository.BaseTranscriptRepository;
import com.app.categorise.data.repository.TranscriptionJobRepository;
import com.app.categorise.domain.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranscriptionJobServiceTest {

    @Mock private TranscriptionJobRepository jobRepository;
    @Mock private BaseTranscriptRepository baseTranscriptRepository;
    @Mock private NotificationService notificationService;

    private TranscriptionJobService service;

    private final UUID userId = UUID.randomUUID();
    private final String videoUrl = "https://www.tiktok.com/@user/video/123";

    @BeforeEach
    void setUp() {
        service = new TranscriptionJobService(jobRepository, baseTranscriptRepository, notificationService);
    }

    @Nested
    @DisplayName("createOrGetExisting")
    class CreateOrGetExisting {

        @Test
        @DisplayName("returns existing COMPLETED job without creating a new one")
        void returnsExistingCompletedJob() {
            TranscriptionJobEntity completedJob = jobWithStatus(JobStatus.COMPLETED);
            when(jobRepository.findTopByUserIdAndVideoUrlOrderByUpdatedAtDesc(userId, videoUrl))
                    .thenReturn(Optional.of(completedJob));

            TranscriptionJobEntity result = service.createOrGetExisting(userId, videoUrl);

            assertThat(result).isSameAs(completedJob);
            verify(jobRepository, never()).save(any());
            verify(baseTranscriptRepository, never()).findByVideoUrl(any());
        }

        @Test
        @DisplayName("returns existing PROCESSING job without creating a new one")
        void returnsExistingProcessingJob() {
            TranscriptionJobEntity processingJob = jobWithStatus(JobStatus.PROCESSING);
            when(jobRepository.findTopByUserIdAndVideoUrlOrderByUpdatedAtDesc(userId, videoUrl))
                    .thenReturn(Optional.of(processingJob));

            TranscriptionJobEntity result = service.createOrGetExisting(userId, videoUrl);

            assertThat(result.getStatus()).isEqualTo(JobStatus.PROCESSING);
            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("returns existing PENDING job without creating a new one")
        void returnsExistingPendingJob() {
            TranscriptionJobEntity pendingJob = jobWithStatus(JobStatus.PENDING);
            when(jobRepository.findTopByUserIdAndVideoUrlOrderByUpdatedAtDesc(userId, videoUrl))
                    .thenReturn(Optional.of(pendingJob));

            TranscriptionJobEntity result = service.createOrGetExisting(userId, videoUrl);

            assertThat(result).isSameAs(pendingJob);
            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates new PENDING job when previous job FAILED (allows retry)")
        void createsNewJobWhenPreviousFailed() {
            TranscriptionJobEntity failedJob = jobWithStatus(JobStatus.FAILED);
            when(jobRepository.findTopByUserIdAndVideoUrlOrderByUpdatedAtDesc(userId, videoUrl))
                    .thenReturn(Optional.of(failedJob));
            when(baseTranscriptRepository.findByVideoUrl(videoUrl))
                    .thenReturn(Optional.empty());

            TranscriptionJobEntity savedJob = jobWithStatus(JobStatus.PENDING);
            when(jobRepository.save(any())).thenReturn(savedJob);

            TranscriptionJobEntity result = service.createOrGetExisting(userId, videoUrl);

            ArgumentCaptor<TranscriptionJobEntity> captor = ArgumentCaptor.forClass(TranscriptionJobEntity.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.PENDING);
            assertThat(captor.getValue().getRetryCount()).isZero();
        }

        @Test
        @DisplayName("creates instant COMPLETED job when previous FAILED but base transcript exists from another user")
        void createsCompletedJobWhenFailedButBaseTranscriptExists() {
            TranscriptionJobEntity failedJob = jobWithStatus(JobStatus.FAILED);
            when(jobRepository.findTopByUserIdAndVideoUrlOrderByUpdatedAtDesc(userId, videoUrl))
                    .thenReturn(Optional.of(failedJob));

            BaseTranscriptEntity baseTranscript = new BaseTranscriptEntity();
            UUID baseTranscriptId = UUID.randomUUID();
            baseTranscript.setId(baseTranscriptId);
            when(baseTranscriptRepository.findByVideoUrl(videoUrl))
                    .thenReturn(Optional.of(baseTranscript));

            TranscriptionJobEntity savedJob = jobWithStatus(JobStatus.COMPLETED);
            when(jobRepository.save(any())).thenReturn(savedJob);

            service.createOrGetExisting(userId, videoUrl);

            ArgumentCaptor<TranscriptionJobEntity> captor = ArgumentCaptor.forClass(TranscriptionJobEntity.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(captor.getValue().getBaseTranscriptId()).isEqualTo(baseTranscriptId);
        }

        @Test
        @DisplayName("creates instant COMPLETED job when base transcript exists but user has no prior job")
        void createsCompletedJobWhenBaseTranscriptExists() {
            when(jobRepository.findTopByUserIdAndVideoUrlOrderByUpdatedAtDesc(userId, videoUrl))
                    .thenReturn(Optional.empty());

            BaseTranscriptEntity baseTranscript = new BaseTranscriptEntity();
            UUID baseTranscriptId = UUID.randomUUID();
            baseTranscript.setId(baseTranscriptId);
            when(baseTranscriptRepository.findByVideoUrl(videoUrl))
                    .thenReturn(Optional.of(baseTranscript));

            TranscriptionJobEntity savedJob = jobWithStatus(JobStatus.COMPLETED);
            when(jobRepository.save(any())).thenReturn(savedJob);

            service.createOrGetExisting(userId, videoUrl);

            ArgumentCaptor<TranscriptionJobEntity> captor = ArgumentCaptor.forClass(TranscriptionJobEntity.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(captor.getValue().getUserId()).isEqualTo(userId);
            assertThat(captor.getValue().getBaseTranscriptId()).isEqualTo(baseTranscriptId);
        }

        @Test
        @DisplayName("creates new PENDING job when no prior job or base transcript exists")
        void createsNewPendingJob() {
            when(jobRepository.findTopByUserIdAndVideoUrlOrderByUpdatedAtDesc(userId, videoUrl))
                    .thenReturn(Optional.empty());
            when(baseTranscriptRepository.findByVideoUrl(any()))
                    .thenReturn(Optional.empty());

            TranscriptionJobEntity savedJob = jobWithStatus(JobStatus.PENDING);
            when(jobRepository.save(any())).thenReturn(savedJob);

            service.createOrGetExisting(userId, videoUrl);

            ArgumentCaptor<TranscriptionJobEntity> captor = ArgumentCaptor.forClass(TranscriptionJobEntity.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.PENDING);
            assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        }
    }

    @Nested
    @DisplayName("handleFailure")
    class HandleFailure {

        @Test
        @DisplayName("marks job as FAILED immediately for permanent 'login required' error")
        void loginRequired_permanentFailure() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            RuntimeException cause = new RuntimeException(
                    "Command failed with exit code: 1. Output: ERROR: [Instagram] DT0-sHxk-pd: "
                    + "Requested content is not available, rate-limit reached or login required.");
            Exception ex = new RuntimeException("Could not process video URL — please check the link and try again.", cause);

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            verify(jobRepository).save(job);
        }

        @Test
        @DisplayName("marks job as FAILED immediately for 'login required' even when deeply wrapped")
        void loginRequired_deeplyWrapped_permanentFailure() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            RuntimeException root = new RuntimeException("login required for this content");
            RuntimeException middle = new RuntimeException("metadata fetch failed", root);
            Exception outer = new RuntimeException("Could not process video URL", middle);

            service.handleFailure(job, outer);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("marks job as FAILED immediately for 'unsupported url' in cause chain")
        void unsupportedUrl_inCauseChain_permanentFailure() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            RuntimeException cause = new RuntimeException("ERROR: Unsupported URL: https://example.com");
            Exception ex = new RuntimeException("Could not process video URL", cause);

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("marks job as FAILED immediately for 'video is too long' error")
        void videoTooLong_permanentFailure() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            Exception ex = new RuntimeException("Video is too long (45 min). Maximum supported duration is 30 minutes.");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("retries transient failure (timeout) with exponential backoff")
        void timeout_retriesWithBackoff() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            Exception ex = new RuntimeException("Connection timeout while downloading");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
            assertThat(job.getRetryCount()).isEqualTo(1);
            assertThat(job.getNextRetryAt()).isNotNull();
        }

        @Test
        @DisplayName("marks as FAILED after max retries exhausted for transient error")
        void maxRetriesExhausted_permanentFailure() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(3); // already at max

            Exception ex = new RuntimeException("Connection timeout while downloading");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("retries generic unknown errors (default to transient)")
        void unknownError_treatedAsTransient() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            Exception ex = new RuntimeException("Something unexpected happened");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
            assertThat(job.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("handles null exception message without NPE")
        void nullMessage_doesNotThrowNPE() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            Exception ex = new RuntimeException((String) null);

            service.handleFailure(job, ex);

            // Should default to transient (retry)
            assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        }

        @Test
        @DisplayName("transient failure with retries remaining re-queues PENDING and never notifies")
        void transientFailure_requeues_andDoesNotNotify() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            Exception ex = new RuntimeException("Connection timeout while downloading");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
            verify(notificationService, never()).notifyJobFailed(any(), any(), any());
        }

        @Test
        @DisplayName("permanent failure marks FAILED and notifies once")
        void permanentFailure_marksFailed_andNotifiesOnce() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            Exception ex = new RuntimeException("login required for this content");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            verify(notificationService, times(1))
                    .notifyJobFailed(eq(job.getUserId()), eq(job.getId()), eq(ex.getMessage()));
        }

        @Test
        @DisplayName("exhausted retries on a transient error notifies exactly once")
        void exhaustedRetries_notifiesOnce() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(3); // at MAX_RETRIES

            Exception ex = new RuntimeException("Connection timeout while downloading");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            verify(notificationService, times(1)).notifyJobFailed(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("markCompleted")
    class MarkCompleted {

        @Test
        @DisplayName("notifies completion with the resolved base-transcript title")
        void notifiesWithResolvedTitle() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setId(UUID.randomUUID());
            UUID baseTranscriptId = UUID.randomUUID();
            UUID userTranscriptId = UUID.randomUUID();

            BaseTranscriptEntity base = new BaseTranscriptEntity();
            base.setId(baseTranscriptId);
            base.setTitle("Resolved Title");
            when(baseTranscriptRepository.findById(baseTranscriptId)).thenReturn(Optional.of(base));

            service.markCompleted(job, baseTranscriptId, userTranscriptId);

            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            verify(notificationService, times(1))
                    .notifyJobCompleted(job.getUserId(), job.getId(), baseTranscriptId, "Resolved Title");
        }

        @Test
        @DisplayName("falls back to 'your video' title when the base transcript title is blank")
        void fallsBackToYourVideoTitle() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setId(UUID.randomUUID());
            UUID baseTranscriptId = UUID.randomUUID();

            BaseTranscriptEntity base = new BaseTranscriptEntity();
            base.setId(baseTranscriptId);
            base.setTitle("   ");
            when(baseTranscriptRepository.findById(baseTranscriptId)).thenReturn(Optional.of(base));

            service.markCompleted(job, baseTranscriptId, UUID.randomUUID());

            verify(notificationService).notifyJobCompleted(any(), any(), eq(baseTranscriptId), eq("your video"));
        }

        @Test
        @DisplayName("does not notify when there is no base transcript id")
        void doesNotNotifyWithoutBaseTranscript() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setId(UUID.randomUUID());

            service.markCompleted(job, null, UUID.randomUUID());

            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            verify(notificationService, never()).notifyJobCompleted(any(), any(), any(), any());
        }
    }

    // --- Helpers ---

    private TranscriptionJobEntity jobWithStatus(JobStatus status) {
        TranscriptionJobEntity job = new TranscriptionJobEntity();
        job.setUserId(userId);
        job.setVideoUrl(videoUrl);
        job.setStatus(status);
        return job;
    }
}
