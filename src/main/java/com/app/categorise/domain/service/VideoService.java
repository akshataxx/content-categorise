package com.app.categorise.domain.service;

import com.app.categorise.data.dto.TikTokMetadata;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.application.internal.ProcessedVideoFiles;
import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.data.client.openai.OpenAIClient;
import com.app.categorise.data.client.whisper.WhisperClient;
import com.app.categorise.data.entity.CategoryAliasEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.BaseTranscriptRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.data.dto.TranscriptCategorisationResult;
import com.app.categorise.util.processExecutor.ProcessExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
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

    private final ProcessExecutor processExecutor;

    private final WhisperClient whisperClient;

    private final OpenAIClient openAIClient;

    private final Executor mediaExecutor;

    private final CategorisationService categorisationService;
    private final CategoryService categoryService;
    private final CategoryAliasService categoryAliasService;
    private final VideoMapper videoMapper;

    private final BaseTranscriptRepository baseTranscriptRepository;
    private final UserTranscriptRepository userTranscriptRepository;

    private final String ffmpegLocation;

    public VideoService(
        @Value("${app.ffmpeg.location}") String ffmpegLocation,
        @Qualifier("mediaExecutor") Executor mediaExecutor,
        BaseTranscriptRepository baseTranscriptRepository,
        CategoryAliasService categoryAliasService,
        CategorisationService categorisationService,
        CategoryService categoryService,
        OpenAIClient openAIClient,
        ProcessExecutor processExecutor,
        UserTranscriptRepository userTranscriptRepository,
        VideoMapper videoMapper,
        WhisperClient whisperClient
    ){
        this.ffmpegLocation = ffmpegLocation;
        this.mediaExecutor = mediaExecutor;
        this.baseTranscriptRepository = baseTranscriptRepository;
        this.categoryAliasService = categoryAliasService;
        this.categorisationService = categorisationService;
        this.categoryService = categoryService;
        this.openAIClient = openAIClient;
        this.processExecutor = processExecutor;
        this.userTranscriptRepository = userTranscriptRepository;
        this.whisperClient = whisperClient;
        this.videoMapper = videoMapper;
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
        // Create a unique temp directory per request to avoid file collisions
        Path tempDir = Files.createTempDirectory("media-" + UUID.randomUUID());
        String baseName = tempDir.resolve("output").toString();
        String outputTemplate = baseName + ".%(ext)s";

        List<String> command = new ArrayList<>();
        command.add("yt-dlp");

        if (isFfmpegLocationValid()) {
            command.add("--ffmpeg-location");
            command.add(ffmpegLocation);
        } else {
            System.out.println("FFmpeg location not configured or invalid; falling back to PATH resolution.");
        }

        command.add("--write-info-json");
        command.add("-f");
        command.add("worstaudio/worst");  // Try audio-only, fallback to worst quality video
        command.add("-x");
        command.add("--audio-format");
        command.add("mp3");
        command.add("--audio-quality");
        command.add("5");
        command.add("-o");
        command.add(outputTemplate);
        command.add(videoUrl);

        processExecutor.run(command.toArray(new String[0]));

        File audioFile = new File(baseName + ".mp3");
        File metadataFile = new File(baseName + ".info.json");
        File videoFile = new File(baseName + ".mp4");  // Potential leftover video file

        return new ProcessedVideoFiles(audioFile, metadataFile, videoFile, tempDir);
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
    public CompletableFuture<TranscriptDtoWithAliases> processVideoAndCreateTranscript(String videoUrl, UUID userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return _processVideoAndCreateTranscript(videoUrl, userId);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, mediaExecutor);
    }

    private TranscriptDtoWithAliases _processVideoAndCreateTranscript(String videoUrl, UUID userId) throws Exception {
        
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
                System.out.println(metadata);

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

        // Extract structured content if not already present
        if (baseTranscript.getStructuredContent() == null || baseTranscript.getStructuredContent().isEmpty()) {
            String structuredContent = openAIClient.extractStructuredContent(
                baseTranscript.getTranscript(),
                baseTranscript.getTitle(),
                categoryName,
                baseTranscript.getDescription()
            );
            baseTranscript.setStructuredContent(structuredContent);
            baseTranscriptRepository.save(baseTranscript);
        }

        // Resolve the alias, use the pre-existing one if it exists, saving the mapping to a category it doesn't exist
        String alias = resolveAlias(userId, category.getId(), categorisationResult.suggestedAlias());

        // Create user transcript association
        UserTranscriptEntity userTranscript = videoMapper.createUserTranscriptEntity(userId, baseTranscript, category);
        userTranscript = userTranscriptRepository.save(userTranscript);

        return videoMapper.buildResponse(baseTranscript, userTranscript, category.getName(), alias);
    }

    private boolean isFfmpegLocationValid() {
        return ffmpegLocation != null
            && !ffmpegLocation.isBlank()
            && new File(ffmpegLocation).exists();
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
