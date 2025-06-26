package com.app.categorise.ui.api.controller;

import com.app.categorise.application.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.application.dto.TikTokMetadata;
import com.app.categorise.application.internal.ProcessedVideoFiles;
import com.app.categorise.domain.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Video", description = "Operations related to processing video URLs")
@RequestMapping("/api/video")
public class VideoController {
    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    /**
     * Rest endpoint to receive video url and get transcript of video
     * @param request the video url
     * @return the transcript of video
     */
    @Operation(summary = "Submit a video URL", description = "Downloads video, extracts transcript and metadata, and saves to DB")
    @PostMapping("/transcribe")
    public ResponseEntity<TranscriptDtoWithAliases> handleVideo(@RequestBody Map<String, String> request) throws Exception {
        System.out.println("POST /api/video/transcribe received");

        String videoUrl = request.get("videoUrl");
        String userId = request.get("userId");

        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("Missing 'videoUrl' in request body");
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Missing 'userId' in request body");
        }

        try (ProcessedVideoFiles files = videoService.extractAudioAndMetadata(videoUrl)) {
            String textTranscript = videoService.transcribeAudio(files.getAudioFile());
            TikTokMetadata metadata = videoService.extractMetadata(files.getMetadataFile());
            TranscriptDtoWithAliases transcriptDto = videoService.processVideoAndCreateTranscript(videoUrl, textTranscript, metadata, userId);
            return ResponseEntity.ok(transcriptDto);
        }
    }
}
