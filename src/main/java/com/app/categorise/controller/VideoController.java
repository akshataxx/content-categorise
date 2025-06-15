package com.app.categorise.controller;

import com.app.categorise.models.entity.Transcript;
import com.app.categorise.models.dto.TikTokMetadata;
import com.app.categorise.models.internal.ProcessedVideoFiles;
import com.app.categorise.service.VideoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/video")
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
    @PostMapping("/url")
    public ResponseEntity<Transcript> handleVideo(@RequestBody Map<String, String> request) throws Exception {
        System.out.println("POST /video/url received");

        String videoUrl = request.get("videoUrl");
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("Missing 'videoUrl' in request body");
        }

        try (ProcessedVideoFiles files = videoService.extractAudioAndMetadata(videoUrl)) {
            String textTranscript = videoService.transcribeAudio(files.getAudioFile());
            TikTokMetadata metadata = videoService.extractMetadata(files.getMetadataFile());
            Transcript transcript = videoService.saveTranscript(videoUrl, textTranscript, metadata);
            return ResponseEntity.ok(transcript);
        }
    }
}
