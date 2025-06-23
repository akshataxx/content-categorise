package com.app.categorise.domain.service;

import com.app.categorise.data.client.whisper.WhisperClient;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.application.dto.TikTokMetadata;
import com.app.categorise.application.internal.ProcessedVideoFiles;
import com.app.categorise.data.repository.TranscriptRepository;
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

    private final CategorisationService categorisationService;

    public VideoService(
            TranscriptRepository transcriptRepository,
            WhisperClient whisperClient,
            CategorisationService categorisationService
    ){
        this.transcriptRepository = transcriptRepository;
        this.whisperClient = whisperClient;
        this.categorisationService = categorisationService;
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

    public TranscriptEntity saveTranscript(String videoUrl, String transcriptText, TikTokMetadata metadata) {
        TranscriptEntity transcriptEntity = new TranscriptEntity();
        transcriptEntity.setVideoUrl(videoUrl);
        transcriptEntity.setTranscript(transcriptText);
        // Set metadata information
        transcriptEntity.setDescription(metadata.getDescription());
        transcriptEntity.setTitle(metadata.getTitle());
        transcriptEntity.setDuration(metadata.getDuration());
        transcriptEntity.setUploadedAt(Instant.ofEpochSecond(metadata.getUploadedEpoch()));
        transcriptEntity.setAccountId(metadata.getAccountId());
        transcriptEntity.setAccount(metadata.getAccount());
        transcriptEntity.setIdentifierId(metadata.getIdentifierId());
        transcriptEntity.setIdentifier(metadata.getIdentifier());

        // Categorise using OpenAI
        List<String> categories = categorisationService.classify(transcriptText, metadata.getTitle(), metadata.getDescription());
        transcriptEntity.setCategories(categories);

        return transcriptRepository.save(transcriptEntity);
    }

    public TikTokMetadata extractMetadata(File metadataFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(metadataFile, TikTokMetadata.class);
    }
}
