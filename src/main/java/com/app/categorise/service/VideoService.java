package com.app.categorise.service;

import com.app.categorise.repository.TranscriptRepository;
import com.app.categorise.util.ProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * handles logic to
 * 1. download video
 * 2. extract audio
 * 3. call Whisper AI
 * 4. Return transcript of video
 */

@Service
public class VideoService {

    private final TranscriptRepository repository;

    public VideoService(TranscriptRepository repository){
        this.repository = repository;
    }

    @Value("${openai.api.key}")
    private String openAiApiKey;

    // download video and extract audio
    public File downloadAndExtractAudio(String videoUrl)throws Exception {
        String videoFile = "video.mp4";
        String audioFile = "audio.mp3";

        System.out.println("Downloading video from: " + videoUrl);

        // use yt-dlp to download video
        ProcessRunner.runCommand("yt-dlp", "--use-extractors", "TikTok", "-t", "mp4", "-o", videoFile, videoUrl);

        System.out.println("yt-dlp succeeded");

        // use ffmpeg to extract audio
        ProcessRunner.runCommand("ffmpeg", "-i", videoFile, "-vn", "-acodec", "mp3", audioFile);

        return new File(audioFile);
    }

    // Step 2: Transcribe audio using OpenAI Whisper API
    public String transcribeAudio(File audioFile) throws Exception {
        // Use curl to send audio file to Whisper API
        ProcessBuilder pb = new ProcessBuilder(
                "curl", "https://api.openai.com/v1/audio/transcriptions",
                "-H", "Authorization: Bearer "+ openAiApiKey,
                "-F", "file=@" + audioFile.getAbsolutePath(),
                "-F", "model=whisper-1"
        );

        pb.redirectErrorStream(true);  // Combine stderr and stdout
        Process process = pb.start();

        // Read API response
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            process.waitFor();
            return response.toString();  // Return transcription response as String
        }
    }
}
