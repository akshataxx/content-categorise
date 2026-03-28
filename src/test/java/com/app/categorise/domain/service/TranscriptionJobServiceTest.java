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

    // --- Helpers ---

    private TranscriptionJobEntity jobWithStatus(JobStatus status) {
        TranscriptionJobEntity job = new TranscriptionJobEntity();
        job.setUserId(userId);
        job.setVideoUrl(videoUrl);
        job.setStatus(status);
        return job;
    }
}
