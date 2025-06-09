package com.app.categorise.controller;

import com.app.categorise.service.VideoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Map;

@RestController
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

    @PostMapping("/video/url")
    public String handleVideo(@RequestBody Map<String, String> request) {
        System.out.println("POST /video/url received ");
        String videoUrl = request.get("videoUrl");
        try {
            File audio = videoService.downloadAndExtractAudio(videoUrl);

            String transcript = videoService.transcribeAudio(audio);

            audio.delete();
            new File("video.mp4").delete();
            return transcript;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}
