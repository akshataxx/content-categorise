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


    public TikTokMetadata extractMetadata(File metadataFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(metadataFile, TikTokMetadata.class);
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
    public TranscriptDtoWithAliases processVideoAndCreateTranscript(String videoUrl, String transcriptText, TikTokMetadata metadata, String userId) throws Exception {
        // Classify and get suggestions from AI
        TranscriptCategorisationResult categorisationResult = categorisationService.classifyAndSuggestAlias(
                transcriptText, metadata.getTitle(), metadata.getDescription()
        );

        // Determine the category and save it if it doesn't exist
        String categoryName = determineCategory(categorisationResult, videoUrl);
        CategoryEntity category = categoryService.saveIfNotExists(categoryName, "", userId);

        // Resolve the alias, use the pre-existing one if it exists, saving the mapping to a category it doesn't exist
        String alias = resolveAlias(userId, category.getId(), categorisationResult.suggestedAlias());

        // Create and save the transcript entity
        TranscriptEntity transcriptEntity = createTranscriptEntity(videoUrl, transcriptText, metadata, userId, category.getId());
        TranscriptEntity savedEntity = transcriptService.save(transcriptEntity);

        return transcriptMapper.toDto(savedEntity, category.getName(), alias);
    }

    // Determine the categoryId. Prioritize the classified categoryId, then the generic topic, then the suggested alias.
    private String determineCategory(TranscriptCategorisationResult result, String videoUrl) throws Exception {
        if (result.category() != null && !result.category().isBlank()) return result.category();
        if (result.genericTopic() != null && !result.genericTopic().isBlank()) return result.genericTopic();
        if (result.suggestedAlias() != null && !result.suggestedAlias().isBlank()) return result.suggestedAlias();
        System.out.println("No category found for video: " + videoUrl);
        throw new Exception("No category found for video: " + videoUrl);
    }

    /** Resolve the alias. If the user has a pre-existing alias for this category, use it.
     *  If not, use the suggested alias from the AI and save it for future use.
     *  TODO: We shouldn't store the generated alias as the category alias, the user should make that choice.
     *  */
    private String resolveAlias(String userId, String categoryId, String suggestedAlias) {
        return categoryAliasService.findByUserIdAndCategoryId(userId, categoryId)
            .map(CategoryAliasEntity::getAlias)
            .orElseGet(() -> {
                if (suggestedAlias != null && !suggestedAlias.isBlank()) {
                    categoryAliasService.saveAlias(userId, categoryId, suggestedAlias);
                    return suggestedAlias;
                }
                return null;
            });
    }

    private TranscriptEntity createTranscriptEntity(String videoUrl, String transcriptText, TikTokMetadata metadata, String userId, String categoryId) {
        TranscriptEntity entity = new TranscriptEntity();
        entity.setVideoUrl(videoUrl);
        entity.setTranscript(transcriptText);
        entity.setDescription(metadata.getDescription());
        entity.setTitle(metadata.getTitle());
        entity.setDuration(metadata.getDuration());
        entity.setUploadedAt(Instant.ofEpochSecond(metadata.getUploadedEpoch()));
        entity.setAccountId(metadata.getAccountId());
        entity.setAccount(metadata.getAccount());
        entity.setIdentifierId(metadata.getIdentifierId());
        entity.setIdentifier(metadata.getIdentifier());
        entity.setUserId(userId);
        entity.setCategoryId(categoryId);
        return entity;
    }

}
