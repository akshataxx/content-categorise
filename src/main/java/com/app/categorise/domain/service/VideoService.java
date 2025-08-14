package com.app.categorise.domain.service;

import com.app.categorise.data.dto.TikTokMetadata;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.application.internal.ProcessedVideoFiles;
import com.app.categorise.application.mapper.TranscriptMapper;
import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.data.client.whisper.WhisperClient;
import com.app.categorise.data.entity.CategoryAliasEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.BaseTranscriptRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.data.dto.TranscriptCategorisationResult;
import com.app.categorise.domain.model.Transcript;
import com.app.categorise.util.ProcessRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
    private final VideoMapper videoMapper;

    private final BaseTranscriptRepository baseTranscriptRepository;
    private final UserTranscriptRepository userTranscriptRepository;

    public VideoService(
        WhisperClient whisperClient,
        CategorisationService categorisationService,
        CategoryService categoryService,
        CategoryAliasService categoryAliasService,
        TranscriptService transcriptService,
        TranscriptMapper transcriptMapper,
        VideoMapper videoMapper,
        BaseTranscriptRepository baseTranscriptRepository,
        UserTranscriptRepository userTranscriptRepository
    ){
        this.whisperClient = whisperClient;
        this.categorisationService = categorisationService;
        this.categoryService = categoryService;
        this.categoryAliasService = categoryAliasService;
        this.transcriptService = transcriptService;
        this.transcriptMapper = transcriptMapper;
        this.videoMapper = videoMapper;
        this.baseTranscriptRepository = baseTranscriptRepository;
        this.userTranscriptRepository = userTranscriptRepository;
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
     * Processes a video URL and creates transcript with deduplication support.
     * 1. Check if transcript already exists for this video URL
     * 2. If exists, reuse it; if not, create new base transcript
     * 3. Check if user already has access to this transcript
     * 4. If user has access, update last accessed; if not, create new user association
     *
     * @param videoUrl The TikTok video URL to process.
     * @param userId The ID of the user submitting the video.
     * @return A {@link TranscriptDtoWithAliases} containing all the necessary information for the client.
     */
    public TranscriptDtoWithAliases processVideoAndCreateTranscript(String videoUrl, UUID userId) throws Exception {
        
        // Check if transcript already exists
        Optional<BaseTranscriptEntity> existingTranscript = 
            baseTranscriptRepository.findByVideoUrl(videoUrl);
        
        BaseTranscriptEntity baseTranscript;
        if (existingTranscript.isPresent()) {
            // Transcript exists, reuse it
            baseTranscript = existingTranscript.get();
            System.out.println("Reusing existing transcript for: " + baseTranscript.getVideoUrl());
        } else {
            // New transcript needed
            try (ProcessedVideoFiles files = extractAudioAndMetadata(videoUrl)) {
                String transcriptText = transcribeAudio(files.getAudioFile());
                TikTokMetadata metadata = extractMetadata(files.getMetadataFile());

                // Create new base transcript
                baseTranscript = videoMapper.createBaseTranscriptEntity(videoUrl, transcriptText, metadata);
                baseTranscript = baseTranscriptRepository.save(baseTranscript);
            }
        }
        
        // Check if user already has this transcript
        Optional<UserTranscriptEntity> existingUserTranscript = 
            userTranscriptRepository.findByUserIdAndBaseTranscriptIdWithBaseTranscript(
                userId, baseTranscript.getId());
        
        if (existingUserTranscript.isPresent()) {
            // User already has this transcript, update last accessed
            UserTranscriptEntity userTranscript = existingUserTranscript.get();
            userTranscript.setLastAccessedAt(Instant.now());
            userTranscriptRepository.save(userTranscript);
            
            return videoMapper.buildResponse(baseTranscript, userTranscript);
        }
        
        // Create new user association with categorization
        TranscriptCategorisationResult categorisationResult = 
            categorisationService.classifyAndSuggestAlias(
                baseTranscript.getTranscript(), 
                baseTranscript.getTitle(), 
                baseTranscript.getDescription()
            );

        // Determine the category and save it if it doesn't exist
        String categoryName = determineCategory(categorisationResult, videoUrl);
        CategoryEntity category = categoryService.saveIfNotExists(categoryName, "", userId);

        // Resolve the alias, use the pre-existing one if it exists, saving the mapping to a category it doesn't exist
        String alias = resolveAlias(userId, category.getId(), categorisationResult.suggestedAlias());
        
        // Create user transcript association
        UserTranscriptEntity userTranscript = videoMapper.createUserTranscriptEntity(userId, baseTranscript, category);
        userTranscript = userTranscriptRepository.save(userTranscript);
        
        return videoMapper.buildResponse(baseTranscript, userTranscript, category.getName(), alias);
    }

    /**
     * LEGACY METHOD - Processes a video from its raw text transcript and metadata into a fully categorized transcript with a user-aware alias.
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
    public TranscriptDtoWithAliases processVideoAndCreateTranscript(String videoUrl, String transcriptText, TikTokMetadata metadata, UUID userId) throws Exception {
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
        TranscriptEntity transcriptEntity = videoMapper.createTranscriptEntity(videoUrl, transcriptText, metadata, userId, category.getId());
        Transcript savedEntity = transcriptService.save(transcriptEntity);

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
    private String resolveAlias(UUID userId, UUID categoryId, String suggestedAlias) {
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



}
