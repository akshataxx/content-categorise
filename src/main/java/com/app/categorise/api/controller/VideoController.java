package com.app.categorise.api.controller;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.domain.model.RateLimitResult;
import com.app.categorise.domain.service.RateLimitService;
import com.app.categorise.domain.service.UntranscribedLinkService;
import com.app.categorise.domain.service.VideoService;
import com.app.categorise.exception.RateLimitExceededException;
import com.app.categorise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Video", description = "Operations related to processing video URLs")
@RequestMapping("/api/video")
public class VideoController {
    private final VideoService videoService;
    private final UntranscribedLinkService untranscribedLinkService;
    private final RateLimitService rateLimitService;

    public VideoController(VideoService videoService, UntranscribedLinkService untranscribedLinkService, 
                          RateLimitService rateLimitService) {
        this.videoService = videoService;
        this.untranscribedLinkService = untranscribedLinkService;
        this.rateLimitService = rateLimitService;
    }

    @Operation(summary = "Submit a video URL", description = "Downloads video, extracts transcript and metadata, and saves to DB")
    @PostMapping("/transcribe")
    public CompletableFuture<ResponseEntity<TranscriptDtoWithAliases>> handleVideo(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal) throws Exception {
        System.out.println("POST /api/video/transcribe received");

        String videoUrl = request.get("videoUrl");
        validateRequest(videoUrl, principal);

        UUID userId = principal.getId();
        
        // Check rate limits before processing
        RateLimitResult rateLimitResult = rateLimitService.checkRateLimit(userId);
        if (!rateLimitResult.isAllowed()) {
            throw new RateLimitExceededException(rateLimitResult);
        }

        return processVideoAsync(videoUrl, userId)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/transcribe-async")
    public ResponseEntity<Void> transcribeAsync(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        System.out.println("POST /api/video/transcribe-async received");

        String videoUrl = request.get("videoUrl");
        validateRequest(videoUrl, principal);
        
        UUID userId = principal.getId();

        // Check rate limits before starting async processing
        RateLimitResult rateLimitResult = rateLimitService.checkRateLimit(userId);
        if (!rateLimitResult.isAllowed()) {
            throw new RateLimitExceededException(rateLimitResult);
        }

        processVideoAsync(videoUrl, userId);
        return ResponseEntity.accepted().build();
    }

    private CompletableFuture<TranscriptDtoWithAliases> processVideoAsync(String videoUrl, UUID userId) {
        return videoService.processVideoAndCreateTranscript(videoUrl, userId)
                .whenComplete((transcriptDto, ex) -> {
                    if (ex == null) {
                        untranscribedLinkService.deleteLink(userId, videoUrl);
                        rateLimitService.recordTranscription(userId);
                    }
                });
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
