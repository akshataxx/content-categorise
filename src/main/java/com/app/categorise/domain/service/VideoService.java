package com.app.categorise.domain.service;

import com.app.categorise.data.dto.VideoMetadata;
import com.app.categorise.domain.model.VideoPlatform;
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
import com.app.categorise.exception.VideoProcessingException;
import com.app.categorise.util.FileUtils;
import com.app.categorise.util.LogSanitizer;
import com.app.categorise.util.processExecutor.ProcessExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private static final String USER_FACING_FETCH_ERROR =
        "Could not process video URL — please check the link and try again.";

    private static final String USER_FACING_PROCESSING_ERROR =
        "Could not process video — incomplete data received. Please try again later.";

    private static final String USER_FACING_CATEGORISATION_ERROR =
        "Could not categorise this video. Please try again later.";

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final ProcessExecutor processExecutor;

    private final WhisperClient whisperClient;

    private final OpenAIClient openAIClient;

    private final ObjectMapper objectMapper;

    private final Executor mediaExecutor;

    private final CategorisationService categorisationService;
    private final CategoryService categoryService;
    private final CategoryAliasService categoryAliasService;
    private final VideoMapper videoMapper;

    private final BaseTranscriptRepository baseTranscriptRepository;
    private final UserTranscriptRepository userTranscriptRepository;

    private final String ffmpegLocation;
    private final int ytDlpTimeoutMinutes;
    private final int metadataTimeoutMinutes;

    public VideoService(
        @Value("${app.ffmpeg.location}") String ffmpegLocation,
        @Value("${app.ytdlp.timeout-minutes}") int ytDlpTimeoutMinutes,
        @Value("${app.ytdlp.metadata-timeout-minutes:1}") int metadataTimeoutMinutes,
        @Qualifier("mediaExecutor") Executor mediaExecutor,
        BaseTranscriptRepository baseTranscriptRepository,
        CategoryAliasService categoryAliasService,
        CategorisationService categorisationService,
        CategoryService categoryService,
        ObjectMapper objectMapper,
        OpenAIClient openAIClient,
        ProcessExecutor processExecutor,
        UserTranscriptRepository userTranscriptRepository,
        VideoMapper videoMapper,
        WhisperClient whisperClient
    ){
        this.ffmpegLocation = ffmpegLocation;
        this.ytDlpTimeoutMinutes = ytDlpTimeoutMinutes;
        this.metadataTimeoutMinutes = metadataTimeoutMinutes;
        this.mediaExecutor = mediaExecutor;
        this.baseTranscriptRepository = baseTranscriptRepository;
        this.categoryAliasService = categoryAliasService;
        this.categorisationService = categorisationService;
        this.categoryService = categoryService;
        this.objectMapper = objectMapper;
        this.openAIClient = openAIClient;
        this.processExecutor = processExecutor;
        this.userTranscriptRepository = userTranscriptRepository;
        this.whisperClient = whisperClient;
        this.videoMapper = videoMapper;
    }

    /**
     * Fetches video metadata by calling {@code yt-dlp --dump-json --no-download}.
     * This also serves as URL validation — if yt-dlp cannot process the URL, it is rejected.
     *
     * @param videoUrl The video URL to fetch metadata for.
     * @return Parsed {@link VideoMetadata} from yt-dlp's JSON output.
     * @throws VideoProcessingException if yt-dlp fails or the output cannot be parsed.
     */
    public VideoMetadata fetchMetadata(String videoUrl) {
        List<String> command = new ArrayList<>();
        command.add("yt-dlp");
        command.add("--dump-json");
        command.add("--no-download");
        command.add("--no-warnings");
        command.add("--user-agent");
        command.add(USER_AGENT);

        if (VideoPlatform.fromUrl(videoUrl) == VideoPlatform.TIKTOK) {
            command.add("--extractor-args");
            command.add("tiktok:api_hostname=api22-normal-c-useast1a.tiktokv.com");
        }

        command.add("--");
        command.add(videoUrl);

        String stdout;
        try {
            stdout = processExecutor.run(metadataTimeoutMinutes, command.toArray(new String[0]));
        } catch (Exception e) {
            log.error("yt-dlp metadata fetch failed for URL [{}]: {}",
                LogSanitizer.sanitize(videoUrl), e.getMessage(), e);
            throw new VideoProcessingException(USER_FACING_FETCH_ERROR, e);
        }

        if (stdout == null || stdout.isBlank()) {
            log.error("yt-dlp returned empty output for URL [{}]", LogSanitizer.sanitize(videoUrl));
            throw new VideoProcessingException(USER_FACING_FETCH_ERROR);
        }

        try {
            return objectMapper.readValue(stdout, VideoMetadata.class);
        } catch (Exception e) {
            log.error("Failed to parse yt-dlp JSON for URL [{}]: {}", LogSanitizer.sanitize(videoUrl), stdout, e);
            throw new VideoProcessingException(USER_FACING_FETCH_ERROR, e);
        }
    }

    /**
     * Downloads the audio track from a video URL using yt-dlp.
     *
     * @param videoUrl The video URL to download audio from.
     * @return A {@link ProcessedVideoFiles} containing the audio file (output.mp3) and temp directory.
     * @throws Exception If the download or extraction process fails.
     */
    public ProcessedVideoFiles downloadAudio(String videoUrl) throws Exception {
        Path tempDir = Files.createTempDirectory("media-" + UUID.randomUUID());
        try {
            String baseName = tempDir.resolve("output").toString();
            String outputTemplate = baseName + ".%(ext)s";

            List<String> command = new ArrayList<>();
            command.add("yt-dlp");

            if (isFfmpegLocationValid()) {
                command.add("--ffmpeg-location");
                command.add(ffmpegLocation);
            } else {
                log.warn("FFmpeg location not configured or invalid; falling back to PATH resolution.");
            }

            // TikTok-specific args for anti-bot measures (only needed for TikTok URLs)
            if (VideoPlatform.fromUrl(videoUrl) == VideoPlatform.TIKTOK) {
                command.add("--extractor-args");
                command.add("tiktok:api_hostname=api22-normal-c-useast1a.tiktokv.com");
            }
            command.add("--user-agent");
            command.add(USER_AGENT);

            command.add("-f");
            command.add("worstaudio/worst");
            command.add("-x");
            command.add("--audio-format");
            command.add("mp3");
            command.add("--audio-quality");
            command.add("5");
            command.add("-o");
            command.add(outputTemplate);
            command.add("--");
            command.add(videoUrl);

            processExecutor.run(ytDlpTimeoutMinutes, command.toArray(new String[0]));

            File audioFile = new File(baseName + ".mp3");

            return new ProcessedVideoFiles(audioFile, tempDir);
        } catch (Exception e) {
            deleteTempDirectory(tempDir);
            throw e;
        }
    }

    private void deleteTempDirectory(Path dir) {
        FileUtils.deleteRecursively(dir);
    }

    // Transcribe audio using OpenAI Whisper API
    public String transcribeAudio(File audioFile) {
        return whisperClient.transcribeAudio(audioFile);
    }

    /**
     * Processes a video URL and creates transcript with deduplication support.
     * 1. Check if transcript already exists for this video URL
     * 2. If exists, reuse it; if not, create new base transcript
     * 3. Check if user already has access to this transcript
     * 4. If user has access, update last accessed; if not, create new user association
     *
     * @param videoUrl The video URL to process.
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
            log.info("Reusing existing transcript for: {}", LogSanitizer.sanitize(baseTranscript.getVideoUrl()));
        } else {
            // New transcript needed — validate URL via metadata fetch, then download audio
            VideoMetadata metadata = fetchMetadata(videoUrl);

            // Fail fast on incomplete metadata BEFORE incurring audio download + Whisper cost
            validateMetadata(metadata, videoUrl);

            try (ProcessedVideoFiles files = downloadAudio(videoUrl)) {
                String transcriptText = transcribeAudio(files.getAudioFile());
                log.debug("Extracted metadata: {}", metadata);
                VideoPlatform platform = metadata.getExtractor() != null
                    ? VideoPlatform.fromExtractor(metadata.getExtractor())
                    : VideoPlatform.fromUrl(videoUrl);

                validateTranscriptText(transcriptText, videoUrl);

                // Create new base transcript
                baseTranscript = videoMapper.createBaseTranscriptEntity(videoUrl, transcriptText, metadata, platform);
                baseTranscript = baseTranscriptRepository.save(baseTranscript);
            }
        }
        
        // Check if user already has this transcript
        Optional<UserTranscriptEntity> existingUserTranscript = 
            userTranscriptRepository.findByUserIdAndBaseTranscript_Id(
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

        // Set the AI-generated title if not already present
        if (baseTranscript.getGeneratedTitle() == null && categorisationResult.generatedTitle() != null) {
            baseTranscript.setGeneratedTitle(categorisationResult.generatedTitle());
        }

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

    /**
     * Validates the metadata returned by yt-dlp BEFORE we incur the cost of
     * downloading audio + calling Whisper. If any required metadata field is
     * missing, we bail out early.
     */
    void validateMetadata(VideoMetadata metadata, String videoUrl) {
        List<String> errors = collectMetadataErrors(metadata);
        if (!errors.isEmpty()) {
            failWithGenericProcessingError(errors, videoUrl);
        }
    }

    /**
     * Validates the transcript text produced by Whisper. Called AFTER
     * transcription since the text isn't available before then.
     */
    void validateTranscriptText(String transcriptText, String videoUrl) {
        if (transcriptText == null || transcriptText.isBlank()) {
            failWithGenericProcessingError(List.of("transcript text is empty"), videoUrl);
        }
    }

    /**
     * Combined validation kept for back-compat with existing callers/tests.
     * Prefer {@link #validateMetadata} (early) + {@link #validateTranscriptText}
     * (after Whisper) at call sites that want fail-fast behaviour.
     */
    void validateTranscriptData(String transcriptText, VideoMetadata metadata, String videoUrl) {
        List<String> errors = new ArrayList<>();
        if (transcriptText == null || transcriptText.isBlank()) {
            errors.add("transcript text is empty");
        }
        errors.addAll(collectMetadataErrors(metadata));
        if (!errors.isEmpty()) {
            failWithGenericProcessingError(errors, videoUrl);
        }
    }

    private List<String> collectMetadataErrors(VideoMetadata metadata) {
        List<String> errors = new ArrayList<>();
        if (metadata.getTitle() == null || metadata.getTitle().isBlank()) {
            errors.add("title is missing");
        }
        if (metadata.getDuration() <= 0) {
            errors.add("duration is invalid (" + metadata.getDuration() + ")");
        }
        if (metadata.getUploadedEpoch() <= 0) {
            errors.add("uploadedAt timestamp is invalid (" + metadata.getUploadedEpoch() + ")");
        }
        return errors;
    }

    private void failWithGenericProcessingError(List<String> errors, String videoUrl) {
        String detailedMessage = "Video processing produced incomplete data for URL ["
            + LogSanitizer.sanitize(videoUrl) + "]: " + String.join(", ", errors);
        log.error(detailedMessage);
        throw new VideoProcessingException(USER_FACING_PROCESSING_ERROR);
    }

    private boolean isFfmpegLocationValid() {
        return ffmpegLocation != null
            && !ffmpegLocation.isBlank()
            && new File(ffmpegLocation).exists();
    }


    // Determine the categoryId. Prioritize the classified categoryId, then the generic topic, then the suggested alias.
    private String determineCategory(TranscriptCategorisationResult result, String videoUrl) {
        if (result.category() != null && !result.category().isBlank()) return result.category();
        if (result.genericTopic() != null && !result.genericTopic().isBlank()) return result.genericTopic();
        if (result.suggestedAlias() != null && !result.suggestedAlias().isBlank()) return result.suggestedAlias();
        log.error("No category found for video: {}", LogSanitizer.sanitize(videoUrl));
        throw new VideoProcessingException(USER_FACING_CATEGORISATION_ERROR);
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
