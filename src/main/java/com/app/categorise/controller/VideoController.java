package com.app.categorise.controller;

import com.app.categorise.entity.Transcript;
import com.app.categorise.dto.TikTokMetadata;
import com.app.categorise.service.VideoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
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
    public String handleVideo(@RequestBody Map<String, String> request) {
        System.out.println("POST /video/url received ");
        String videoUrl = request.get("videoUrl");
        try {
            List<File> files = videoService.extractAudioAndMetadata(videoUrl);
            File audioFile = files.get(0);
            File metadataFile = files.get(1);

            System.out.println("Transcribing audio...");
            String textTranscript = videoService.transcribeAudio(audioFile);
            System.out.println("Transcript: " + textTranscript);
            if (!audioFile.delete()) {
                System.out.println("Failed to delete audio file");
            }

            TikTokMetadata metadata = videoService.extractMetadata(metadataFile);
            System.out.println("Metadata: " + metadata);
            if (!metadataFile.delete()) {
                System.out.println("Failed to delete metadata file");
            }

            Transcript transcript = videoService.saveTranscript(videoUrl, textTranscript, metadata);

            return transcript.toString();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " + e.getMessage());
            return e.getMessage();
        }
    }
}
