package com.app.categorise.api.controller;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.domain.service.UntranscribedLinkService;
import com.app.categorise.domain.service.VideoService;
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

    public VideoController(VideoService videoService, UntranscribedLinkService untranscribedLinkService) {
        this.videoService = videoService;
        this.untranscribedLinkService = untranscribedLinkService;
    }

    @Operation(summary = "Submit a video URL", description = "Downloads video, extracts transcript and metadata, and saves to DB")
    @PostMapping("/transcribe")
    public ResponseEntity<TranscriptDtoWithAliases> handleVideo(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal) throws Exception {
        System.out.println("POST /api/video/transcribe received");

        String videoUrl = request.get("videoUrl");
        validateRequest(videoUrl, principal);

        UUID userId = principal.getId();
        TranscriptDtoWithAliases result = processVideo(videoUrl, userId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/transcribe-async")
    public ResponseEntity<Void> transcribeAsync(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        System.out.println("POST /api/video/transcribe-async received");

        String videoUrl = request.get("videoUrl");
        validateRequest(videoUrl, principal);
        
        UUID userId = principal.getId();

        CompletableFuture.runAsync(() -> {
            try {
                processVideo(videoUrl, userId);
            } catch (Exception e) {
                System.err.println("Error during async transcription: " + e.getMessage());
                e.printStackTrace();
            }
        });

        return ResponseEntity.accepted().build();
    }

    private TranscriptDtoWithAliases processVideo(String videoUrl, UUID userId) throws Exception {
        TranscriptDtoWithAliases transcriptDto = videoService.processVideoAndCreateTranscript(videoUrl, userId);
        System.out.println("TranscriptDto: " + transcriptDto);

        untranscribedLinkService.deleteLink(userId, videoUrl);

        return transcriptDto;
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
