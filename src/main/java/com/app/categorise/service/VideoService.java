package com.app.categorise.service;

import com.app.categorise.entity.Transcript;
import com.app.categorise.repository.TranscriptRepository;
import com.app.categorise.util.ProcessRunner;
import com.app.categorise.client.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;

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
        String audioFile = "audio.mp3";

        // Use yt-dlp to download the audio, it will use ffmpeg to extract audio
        ProcessRunner.runCommand("yt-dlp", "--use-extractors", "TikTok", "-x", "--audio-format", "mp3", "--audio-quality", "5", "-o", audioFile, videoUrl);

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
