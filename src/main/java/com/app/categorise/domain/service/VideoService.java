package com.app.categorise.domain.service;

import com.app.categorise.data.dto.TikTokMetadata;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.application.internal.ProcessedVideoFiles;
import com.app.categorise.application.mapper.TranscriptMapper;
import com.app.categorise.data.client.whisper.WhisperClient;
import com.app.categorise.data.entity.CategoryAliasEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.data.dto.TranscriptCategorisationResult;
import com.app.categorise.util.ProcessRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.Optional;

/**
 * handles logic to
 * 1. download video
 * 2. extract audio
 * 3. call Whisper AI
 * 4. Return transcript of video
 */

@Service
public class VideoService {

    private final WhisperClient whisperClient;

    private final CategorisationService categorisationService;
    private final CategoryService categoryService;
    private final CategoryAliasService categoryAliasService;
    private final TranscriptService transcriptService;
    private final TranscriptMapper transcriptMapper;

    public VideoService(
        WhisperClient whisperClient,
        CategorisationService categorisationService,
        CategoryService categoryService,
        CategoryAliasService categoryAliasService,
        TranscriptService transcriptService,
        TranscriptMapper transcriptMapper
    ){
        this.whisperClient = whisperClient;
        this.categorisationService = categorisationService;
        this.categoryService = categoryService;
        this.categoryAliasService = categoryAliasService;
        this.transcriptService = transcriptService;
        this.transcriptMapper = transcriptMapper;
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

    /**
     * Processes a video from its raw text transcript and metadata into a fully categorized transcript with a user-aware alias.
     * This method orchestrates the core logic:
     * 1. Calls the AI service to get a classification result (canonical categoryId, generic topic, and suggested alias).
     * 2. Determines the correct grouping key (prioritizing the canonical categoryId).
     * 3. Checks if the user has a pre-existing alias for this grouping key. If so, uses it. If not, uses the AI's suggestion and saves it for future use.
     * 4. Saves the final transcript entity to the database with the correct alias and categoryId info.
     * 5. Maps the saved entity to a DTO to be returned by the API.
     *
     * @param videoUrl The original URL of the video.
     * @param transcriptText The text transcript of the video.
     * @param metadata The metadata extracted from the video.
     * @param userId The ID of the user submitting the video.
     * @return A {@link TranscriptDtoWithAliases} containing all the necessary information for the client.
     */
    public TranscriptDtoWithAliases processVideoAndCreateTranscript(String videoUrl, String transcriptText, TikTokMetadata metadata, String userId) {
        // Classify and get suggestions from AI
        TranscriptCategorisationResult categorisationResult = categorisationService.classifyAndSuggestAlias(
                transcriptText, metadata.getTitle(), metadata.getDescription()
        );

        // Determine the categoryId. Prioritize the classified categoryId.
        String classificationCategory = categorisationResult.category();
        String classificationTopic = categorisationResult.genericTopic();
        String classificationAlias = categorisationResult.suggestedAlias();
        String category = null;
        if (classificationCategory != null && !classificationCategory.isBlank()) {
            category = classificationCategory;
        } else if (classificationTopic != null && !classificationTopic.isBlank()) {
            category = classificationTopic;
        } else if (classificationAlias != null && !classificationAlias.isBlank()) {
            category = classificationAlias;
        } else {
            System.out.println("No categoryId found for video: " + videoUrl);
            category = "Unknown";
        }

        Optional<CategoryEntity> existingCategory = categoryService.findCategoryByName(category);
        Optional<String> existingAlias = existingCategory.flatMap(c ->
            categoryAliasService.findByUserIdAndCategoryId(userId, c.getId()
        ).map(CategoryAliasEntity::getAlias));

        String finalCategory = category;
        CategoryEntity categoryEntity = existingCategory.orElseGet(() ->
            categoryService.saveCategory(null, finalCategory, "", userId)
        );

        String finalAlias = null;
        if (existingAlias.isPresent()) {
            finalAlias = existingAlias.get();
        } else if (classificationAlias != null && !classificationAlias.isBlank()) {
            // Save the new suggestion in the users alias list for a categoryId
            categoryAliasService.saveAlias(userId, categoryEntity.getId(), classificationAlias);
            // Use the categoryId name as the alias
            finalAlias = classificationAlias;
        }

        // Create and save the transcript entity
        TranscriptEntity transcriptEntity = new TranscriptEntity();
        transcriptEntity.setVideoUrl(videoUrl);
        transcriptEntity.setTranscript(transcriptText);
        transcriptEntity.setDescription(metadata.getDescription());
        transcriptEntity.setTitle(metadata.getTitle());
        transcriptEntity.setDuration(metadata.getDuration());
        transcriptEntity.setUploadedAt(Instant.ofEpochSecond(metadata.getUploadedEpoch()));
        transcriptEntity.setAccountId(metadata.getAccountId());
        transcriptEntity.setAccount(metadata.getAccount());
        transcriptEntity.setIdentifierId(metadata.getIdentifierId());
        transcriptEntity.setIdentifier(metadata.getIdentifier());
        transcriptEntity.setUserId(userId);
        transcriptEntity.setCategoryId(categoryEntity.getId());

        TranscriptEntity savedEntity = transcriptService.save(transcriptEntity);

        // Map to DTO and return
        return transcriptMapper.toDto(savedEntity, categoryEntity.getName(), finalAlias);
    }

    public TikTokMetadata extractMetadata(File metadataFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(metadataFile, TikTokMetadata.class);
    }
}
