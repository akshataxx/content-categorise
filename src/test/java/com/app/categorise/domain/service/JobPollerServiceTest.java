package com.app.categorise.domain.service;

import com.app.categorise.data.entity.TranscriptionJobEntity;
import com.app.categorise.data.repository.TranscriptionJobRepository;
import com.app.categorise.domain.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPollerServiceTest {

    @Mock private TranscriptionJobRepository jobRepository;
    @Mock private TranscriptionJobService jobService;
    @Mock private VideoService videoService;
    @Mock private RateLimitService rateLimitService;

    private JobPollerService jobPollerService;
    private TranscriptionJobEntity job;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        jobPollerService = new JobPollerService(
            jobRepository,
            jobService,
            videoService,
            rateLimitService,
            directExecutor
        );

        job = new TranscriptionJobEntity();
        job.setId(UUID.randomUUID());
        job.setUserId(UUID.randomUUID());
        job.setVideoUrl("https://www.tiktok.com/@example/video/123");
    }

    @Test
    void schedulesTranscriptionOnlyOnceOnTheMediaExecutor() throws Exception {
        when(jobRepository.claimNextPending()).thenReturn(Optional.of(job));
        when(rateLimitService.checkRateLimit(job.getUserId())).thenReturn(
            RateLimitResult.allowed(1, Instant.now(), RateLimitResult.RateLimitType.PER_MINUTE)
        );
        doThrow(new RuntimeException("transcription failed"))
            .when(videoService)
            .processVideoAndCreateTranscriptSynchronously(job.getVideoUrl(), job.getUserId());

        jobPollerService.pollAndProcess();

        verify(videoService).processVideoAndCreateTranscriptSynchronously(job.getVideoUrl(), job.getUserId());
        verify(videoService, never()).processVideoAndCreateTranscript(job.getVideoUrl(), job.getUserId());
        verify(jobService).handleFailure(org.mockito.ArgumentMatchers.same(job), org.mockito.ArgumentMatchers.any(RuntimeException.class));
    }
}
