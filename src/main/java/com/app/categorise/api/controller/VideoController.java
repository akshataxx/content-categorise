package com.app.categorise.api.controller;

import com.app.categorise.api.dto.JobSubmissionResponse;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.TranscriptionJobEntity;
import com.app.categorise.domain.model.JobStatus;
import com.app.categorise.domain.model.RateLimitResult;
import com.app.categorise.domain.service.RateLimitService;
import com.app.categorise.domain.service.TranscriptionJobService;
import com.app.categorise.domain.service.VideoService;
import com.app.categorise.exception.RateLimitExceededException;
import com.app.categorise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Video", description = "Operations related to processing video URLs")
@RequestMapping("/api/video")
public class VideoController {
    private static final Logger log = LoggerFactory.getLogger(VideoController.class);
    
    private final VideoService videoService;
    private final TranscriptionJobService transcriptionJobService;
    private final RateLimitService rateLimitService;

    public VideoController(VideoService videoService, TranscriptionJobService transcriptionJobService,
                          RateLimitService rateLimitService) {
        this.videoService = videoService;
        this.transcriptionJobService = transcriptionJobService;
        this.rateLimitService = rateLimitService;
    }

    @Operation(summary = "Submit a video URL", description = "Downloads video, extracts transcript and metadata, and saves to DB")
    @PostMapping("/transcribe")
    public CompletableFuture<ResponseEntity<TranscriptDtoWithAliases>> handleVideo(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String videoUrl = request.get("videoUrl");
        log.info("POST /api/video/transcribe received for videoUrl={}", videoUrl);

        validateRequest(videoUrl, principal);

        UUID userId = principal.getId();
        
        // Check rate limits before processing
        RateLimitResult rateLimitResult = rateLimitService.checkRateLimit(userId);
        if (!rateLimitResult.isAllowed()) {
            throw new RateLimitExceededException(rateLimitResult);
        }

        // Create durable job row
        TranscriptionJobEntity job = transcriptionJobService.createOrGetExisting(userId, videoUrl);
        boolean isNewJob = job.getStatus() == JobStatus.PENDING;

        if (isNewJob) {
            transcriptionJobService.transitionToProcessing(job);
        }

        // Run pipeline inline (same as before) - handles base transcript dedup internally
        return videoService.processVideoAndCreateTranscript(videoUrl, userId)
                .whenComplete((transcriptDto, ex) -> {
                    if (ex == null) {
                        if (isNewJob) {
                            transcriptionJobService.markCompletedForUrl(job, videoUrl);
                        }
                        rateLimitService.recordTranscription(userId);
                        log.info("Transcription completed for jobId={}, videoUrl={}", job.getId(), videoUrl);
                    } else {
                        Throwable cause = (ex instanceof CompletionException) ? ex.getCause() : ex;
                        if (isNewJob) {
                            transcriptionJobService.handleFailure(job,
                                    (cause instanceof Exception) ? (Exception) cause : new RuntimeException(cause));
                        }
                    }
                })
                .thenApply(ResponseEntity::ok);
    }

    @Operation(summary = "Submit a video URL for async transcription", description = "Creates a durable job and returns immediately with job ID")
    @PostMapping("/transcribe-async")
    public ResponseEntity<JobSubmissionResponse> transcribeAsync(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String videoUrl = request.get("videoUrl");
        log.info("POST /api/video/transcribe-async received for videoUrl={}", videoUrl);

        validateRequest(videoUrl, principal);
        
        UUID userId = principal.getId();

        // Check rate limits before accepting job
        RateLimitResult rateLimitResult = rateLimitService.checkRateLimit(userId);
        if (!rateLimitResult.isAllowed()) {
            throw new RateLimitExceededException(rateLimitResult);
        }

        // Create durable job row - poller picks it up (WP03)
        TranscriptionJobEntity job = transcriptionJobService.createOrGetExisting(userId, videoUrl);

        return ResponseEntity.accepted()
                .body(new JobSubmissionResponse(job.getId(), job.getStatus().name()));
    }

    private void validateRequest(String videoUrl, UserPrincipal principal) {
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("Missing 'videoUrl' in request body");
        }
        if (principal == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
    }
}
