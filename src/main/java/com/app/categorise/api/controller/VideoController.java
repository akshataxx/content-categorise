package com.app.categorise.api.controller;

import com.app.categorise.data.dto.TikTokMetadata;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.application.internal.ProcessedVideoFiles;
import com.app.categorise.domain.service.UntranscribedLinkService;
import com.app.categorise.domain.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * Rest endpoint to receive video url and get transcript of video
     * @param request the video url
*    * @param userId the user id
     * @return the transcript of video
     */
    @Operation(summary = "Submit a video URL", description = "Downloads video, extracts transcript and metadata, and saves to DB")
    @PostMapping("/transcribe")
    public ResponseEntity<TranscriptDtoWithAliases> handleVideo(@RequestBody Map<String, String> request) throws Exception {
        System.out.println("POST /api/video/transcribe received");

        String videoUrl = request.get("videoUrl");

        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("Missing 'videoUrl' in request body");
        }

        if (request.get("userId") == null) {
            throw new IllegalArgumentException("Missing 'userId' in request body");
        }
        UUID userId = UUID.fromString(request.get("userId"));

        try (ProcessedVideoFiles files = videoService.extractAudioAndMetadata(videoUrl)) {
            System.out.println("Audio file: " + files.getAudioFile().getAbsolutePath());
            String textTranscript = videoService.transcribeAudio(files.getAudioFile());
            System.out.println("Transcript: " + textTranscript);
            TikTokMetadata metadata = videoService.extractMetadata(files.getMetadataFile());
            System.out.println("Metadata: " + metadata);
            TranscriptDtoWithAliases transcriptDto = videoService.processVideoAndCreateTranscript(videoUrl, textTranscript, metadata, userId);
            System.out.println("TranscriptDto: " + transcriptDto);
            // remove from untranscribed list if present
            untranscribedLinkService.deleteLink(userId, videoUrl);
            return ResponseEntity.ok(transcriptDto);
        }
    }
}
