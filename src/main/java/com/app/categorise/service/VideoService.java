package com.app.categorise.service;

import com.app.categorise.entity.Transcript;
import com.app.categorise.repository.TranscriptRepository;
import com.app.categorise.util.ProcessRunner;
import com.app.categorise.client.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;

/**
 * handles logic to
 * 1. download video
 * 2. extract audio
 * 3. call Whisper AI
 * 4. Return transcript of video
 */

@Service
public class VideoService {

    private final TranscriptRepository transcriptRepository;
    private final WhisperClient whisperClient;

    public VideoService(TranscriptRepository transcriptRepository, WhisperClient whisperClient){
        this.transcriptRepository = transcriptRepository;
        this.whisperClient = whisperClient;
    }

    @Value("${openai.api.key}")
    private String openAiApiKey;

    // Download video and extract audio
    public File downloadAndExtractAudio(String videoUrl) throws Exception {
        String videoFile = "video.mp4";
        String audioFile = "audio.mp3";

        // use yt-dlp to download video
        ProcessRunner.runCommand("yt-dlp", "--use-extractors", "TikTok", "-t", "mp4", "-o", videoFile, videoUrl);

        System.out.println("Extracting audio...");

        // use ffmpeg to extract audio
        ProcessRunner.runCommand("ffmpeg", "-i", videoFile, "-vn", "-acodec", "mp3", audioFile);

        boolean deleted = new File(videoFile).delete();
        if (!deleted) {
            System.out.println("Failed to delete video file");
        }

        return new File(audioFile);
    }

    // Transcribe audio using OpenAI Whisper API
    public String transcribeAudio(File audioFile, String videoUrl) {
        String transcriptText = whisperClient.transcribeAudio(audioFile);

        Transcript transcript = createAndSaveTranscript(videoUrl, transcriptText);
        return transcript.getTranscript();
    }

    private Transcript createAndSaveTranscript(String videoUrl, String transcriptText) {
        Transcript transcript = new Transcript();
        transcript.setVideoUrl(videoUrl);
        transcript.setTranscript(transcriptText);
        return transcriptRepository.save(transcript);
    }
}
