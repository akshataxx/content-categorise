package com.app.categorise.service;

import com.app.categorise.client.whisper.WhisperClient;
import com.app.categorise.entity.Transcript;
import com.app.categorise.dto.TikTokMetadata;
import com.app.categorise.model.ProcessedVideoFiles;
import com.app.categorise.repository.TranscriptRepository;
import com.app.categorise.util.ProcessRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.io.File;
import java.time.Instant;
import java.util.List;

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

    /**
     * @param videoUrl The TikTok video URL to download and extract audio from.
     * @return A list containing two files:
     *         <ul>
     *             <li>Index 0: The audio file (output.mp3)</li>
     *             <li>Index 1: The metadata file (output.info.json)</li>
     *         </ul>
     * @throws Exception If the download or extraction process fails.
     */
    public ProcessedVideoFiles extractAudioAndMetadata(String videoUrl) throws Exception {
        String baseName = "output";
        String outputTemplate = baseName + ".%(ext)s";

        ProcessRunner.runCommand(
                "yt-dlp",
                "--use-extractors", "TikTok",
                "--write-info-json",
                "-x", "--audio-format", "mp3", "--audio-quality", "5",
                "-o", outputTemplate,
                videoUrl
        );

        File audioFile = new File(baseName + ".mp3");
        File metadataFile = new File(baseName + ".info.json");

        return new ProcessedVideoFiles(audioFile, metadataFile);
    }

    // Transcribe audio using OpenAI Whisper API
    public String transcribeAudio(File audioFile) {
        return whisperClient.transcribeAudio(audioFile);
    }

    public Transcript saveTranscript(String videoUrl, String transcriptText, TikTokMetadata metadata) {
        Transcript transcript = new Transcript();
        transcript.setVideoUrl(videoUrl);
        transcript.setTranscript(transcriptText);
        // Set metadata information
        transcript.setDescription(metadata.getDescription());
        transcript.setTitle(metadata.getTitle());
        transcript.setDuration(metadata.getDuration());
        transcript.setUploadedAt(Instant.ofEpochSecond(metadata.getUploadedEpoch()));
        transcript.setAccountId(metadata.getAccountId());
        transcript.setAccount(metadata.getAccount());
        transcript.setIdentifierId(metadata.getIdentifierId());
        transcript.setIdentifier(metadata.getIdentifier());
        return transcriptRepository.save(transcript);
    }

    public TikTokMetadata extractMetadata(File metadataFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(metadataFile, TikTokMetadata.class);
    }
}
