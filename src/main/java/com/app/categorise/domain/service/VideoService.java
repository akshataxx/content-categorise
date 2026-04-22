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
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final String USER_FACING_DURATION_LIMIT_ERROR_TEMPLATE =
        "Video is too long (%d min). Maximum supported duration is %d minutes.";

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
    private final String ytDlpLocation;
    private final int ytDlpTimeoutMinutes;
    private final int metadataTimeoutMinutes;
    private final int maxVideoDurationMinutes;

    public VideoService(
        @Value("${app.ffmpeg.location}") String ffmpegLocation,
        @Value("${app.ytdlp.location:}") String ytDlpLocation,
        @Value("${app.ytdlp.timeout-minutes}") int ytDlpTimeoutMinutes,
        @Value("${app.ytdlp.metadata-timeout-minutes:1}") int metadataTimeoutMinutes,
        @Value("${app.video.max-duration-minutes:10}") int maxVideoDurationMinutes,
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
        this.ytDlpLocation = (ytDlpLocation == null || ytDlpLocation.isBlank()) ? "yt-dlp" : ytDlpLocation;
        this.ytDlpTimeoutMinutes = ytDlpTimeoutMinutes;
        this.metadataTimeoutMinutes = metadataTimeoutMinutes;
        this.maxVideoDurationMinutes = maxVideoDurationMinutes;
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
        long startMs = System.currentTimeMillis();
        List<String> command = new ArrayList<>();
        command.add(ytDlpLocation);
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

        log.info("[transcription] fetching_metadata url={}", LogSanitizer.sanitize(videoUrl));
        String stdout;
        try {
            stdout = processExecutor.run(metadataTimeoutMinutes, command.toArray(new String[0]));
        } catch (Exception e) {
            log.error("[transcription] metadata_fetch_failed url={} error={}",
                LogSanitizer.sanitize(videoUrl), e.getMessage(), e);
            throw new VideoProcessingException(USER_FACING_FETCH_ERROR, e);
        }

        if (stdout == null || stdout.isBlank()) {
            log.error("[transcription] metadata_fetch_failed url={} reason=empty_output",
                LogSanitizer.sanitize(videoUrl));
            throw new VideoProcessingException(USER_FACING_FETCH_ERROR);
        }

        log.info(stdout);

        VideoMetadata metadata;
        try {
            metadata = objectMapper.readValue(stdout, VideoMetadata.class);
        } catch (Exception e) {
            log.error("[transcription] metadata_parse_failed url={} stdout={}",
                LogSanitizer.sanitize(videoUrl), stdout, e);
            throw new VideoProcessingException(USER_FACING_FETCH_ERROR, e);
        }

        log.info("[transcription] metadata_fetched title=\"{}\" platform={} duration_s={} elapsed_ms={}",
            metadata.getTitle(), metadata.getExtractor(), metadata.getDuration(),
            System.currentTimeMillis() - startMs);
        return metadata;
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
        long startMs = System.currentTimeMillis();
        try {
            String baseName = tempDir.resolve("output").toString();
            String outputTemplate = baseName + ".%(ext)s";

            List<String> command = new ArrayList<>();
            command.add(ytDlpLocation);

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

            log.info("[transcription] downloading_audio url={}", LogSanitizer.sanitize(videoUrl));
            processExecutor.run(ytDlpTimeoutMinutes, command.toArray(new String[0]));

            File audioFile = new File(baseName + ".mp3");
            long sizeKb = audioFile.exists() ? audioFile.length() / 1024 : -1;
            log.info("[transcription] audio_downloaded size_kb={} elapsed_ms={}",
                sizeKb, System.currentTimeMillis() - startMs);

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
        long startMs = System.currentTimeMillis();
        log.debug("[transcription] transcribing_audio file={} size_kb={}",
            audioFile.getName(), audioFile.length() / 1024);
        String text = whisperClient.transcribeAudio(audioFile);
        log.info("[transcription] transcribed chars={} elapsed_ms={}",
            text == null ? 0 : text.length(), System.currentTimeMillis() - startMs);
        return text;
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
        long pipelineStartMs = System.currentTimeMillis();
        log.info("[transcription] starting url={} user={}", LogSanitizer.sanitize(videoUrl), userId);

        // Tier-1 dedup: check if a base transcript already exists for this exact URL
        Optional<BaseTranscriptEntity> existingTranscript =
            baseTranscriptRepository.findByVideoUrl(videoUrl);

        BaseTranscriptEntity baseTranscript;
        if (existingTranscript.isPresent()) {
            baseTranscript = existingTranscript.get();
            log.info("[transcription] cache_hit tier=1_url base_transcript_id={} url={}",
                baseTranscript.getId(), LogSanitizer.sanitize(baseTranscript.getVideoUrl()));
        } else {
            log.info("[transcription] cache_miss tier=1_url url={}", LogSanitizer.sanitize(videoUrl));
            // New transcript candidate — validate URL via metadata fetch, then check Tier-2 dedup
            VideoMetadata metadata = fetchMetadata(videoUrl);

            VideoPlatform platform = metadata.getExtractor() != null
                ? VideoPlatform.fromExtractor(metadata.getExtractor())
                : VideoPlatform.fromUrl(videoUrl);

            // Tier-2 dedup: same underlying video shared via different URL forms
            // (e.g. youtu.be/X vs youtube.com/watch?v=X). If we already have a
            // transcript for this canonical (platform, video_id) we reuse it
            // and skip audio download + Whisper + categorisation entirely.
            BaseTranscriptEntity canonicalMatch = findCanonicalMatch(platform, metadata.getId());
            if (canonicalMatch != null) {
                log.info("[transcription] cache_hit tier=2_canonical base_transcript_id={} platform={} video_id={} requested_url={}",
                    canonicalMatch.getId(), platform, metadata.getId(),
                    LogSanitizer.sanitize(videoUrl));
                baseTranscript = canonicalMatch;
            } else {
                // Fail fast on incomplete metadata BEFORE incurring audio download + Whisper cost
                validateMetadata(metadata, videoUrl);

                // Reject videos longer than the configured cap before paying for audio + Whisper
                validateDurationLimit(metadata, videoUrl);

                try (ProcessedVideoFiles files = downloadAudio(videoUrl)) {
                    String transcriptText = transcribeAudio(files.getAudioFile());
                    validateTranscriptText(transcriptText, videoUrl);

                    // Create new base transcript with canonical id populated
                    BaseTranscriptEntity entity =
                        videoMapper.createBaseTranscriptEntity(videoUrl, transcriptText, metadata, platform);
                    baseTranscript = saveOrReuseOnConflict(entity, platform, metadata.getId(), videoUrl);
                    log.info("[transcription] base_transcript_saved id={} platform={} video_id={} chars={}",
                        baseTranscript.getId(), platform, metadata.getId(), transcriptText.length());
                }
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
            log.info("[transcription] complete user={} base_transcript_id={} user_transcript_id={} reused=true total_ms={}",
                userId, baseTranscript.getId(), userTranscript.getId(),
                System.currentTimeMillis() - pipelineStartMs);

            return videoMapper.buildResponse(baseTranscript, userTranscript);
        }

        // Create new user association with categorization
        log.debug("[transcription] categorising base_transcript_id={}", baseTranscript.getId());
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
        log.info("[transcription] categorised category=\"{}\" alias=\"{}\" base_transcript_id={}",
            categoryName, categorisationResult.suggestedAlias(), baseTranscript.getId());

        // Extract structured content if not already present
        if (baseTranscript.getStructuredContent() == null || baseTranscript.getStructuredContent().isEmpty()) {
            log.debug("[transcription] extracting_structured_content base_transcript_id={}", baseTranscript.getId());
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

        log.info("[transcription] complete user={} base_transcript_id={} user_transcript_id={} reused=false total_ms={}",
            userId, baseTranscript.getId(), userTranscript.getId(),
            System.currentTimeMillis() - pipelineStartMs);

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
     * Rejects videos longer than the configured cap. Called immediately after
     * {@link #validateMetadata} so we never download audio or call Whisper for
     * an over-limit video.
     *
     * <p>Uses a per-video-type-agnostic message ("Video is too long, max is X min")
     * because this is expected user input — not an error condition.
     */
    void validateDurationLimit(VideoMetadata metadata, String videoUrl) {
        long durationSeconds = metadata.getDuration();
        long durationMinutes = (durationSeconds + 59) / 60; // round up so 9m01s reads as 10
        if (durationMinutes > maxVideoDurationMinutes) {
            log.info("[transcription] rejected_too_long url={} duration_seconds={} duration_minutes={} limit_minutes={}",
                LogSanitizer.sanitize(videoUrl), durationSeconds, durationMinutes, maxVideoDurationMinutes);
            throw new VideoProcessingException(
                String.format(USER_FACING_DURATION_LIMIT_ERROR_TEMPLATE, durationMinutes, maxVideoDurationMinutes)
            );
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

    /**
     * Looks up an existing base transcript by canonical {@code (platform, videoId)}.
     * Returns {@code null} when either input is missing/blank — in that case
     * we cannot reliably dedup and must fall through to the full pipeline.
     */
    private BaseTranscriptEntity findCanonicalMatch(VideoPlatform platform, String videoId) {
        if (platform == null || videoId == null || videoId.isBlank()) {
            return null;
        }
        return baseTranscriptRepository
            .findByPlatformAndPlatformVideoId(platform.name(), videoId)
            .orElse(null);
    }

    /**
     * Saves a freshly built base transcript while gracefully handling the race
     * where two concurrent first-time submissions of the same video (via
     * different URLs) both reach this point. The DB unique index on
     * {@code (platform, platform_video_id)} rejects the second insert; we catch
     * that and re-query so the loser of the race reuses the winner's row.
     */
    private BaseTranscriptEntity saveOrReuseOnConflict(BaseTranscriptEntity entity,
                                                       VideoPlatform platform,
                                                       String videoId,
                                                       String videoUrl) {
        try {
            return baseTranscriptRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // Likely a concurrent insert won the race. Re-query by canonical id;
            // if found, reuse it. Otherwise the conflict was on something else
            // (e.g. duplicate video_url) — re-throw so callers see the real error.
            BaseTranscriptEntity existing = findCanonicalMatch(platform, videoId);
            if (existing != null) {
                log.info("[transcription] race_resolved_via_canonical base_transcript_id={} platform={} video_id={} url={}",
                    existing.getId(), platform, videoId, LogSanitizer.sanitize(videoUrl));
                return existing;
            }
            log.error("[transcription] save_conflict_unresolved url={} platform={} video_id={}",
                LogSanitizer.sanitize(videoUrl), platform, videoId, e);
            throw e;
        }
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
