package com.app.categorise.domain.service;

import com.app.categorise.application.internal.ProcessedVideoFiles;
import com.app.categorise.config.TikTokIngestionProperties;
import com.app.categorise.data.client.tiktok.TikTokEmbedFallbackClient;
import com.app.categorise.data.client.tiktok.TikTokEmbedMedia;
import com.app.categorise.data.dto.VideoMetadata;
import com.app.categorise.exception.VideoProcessingException;
import com.app.categorise.util.FileUtils;
import com.app.categorise.util.processExecutor.ProcessExecutionException;
import com.app.categorise.util.processExecutor.ProcessExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class TikTokIngestionService {
    private final String ffmpegLocation;
    private final String ytDlpLocation;
    private final int ytDlpTimeoutMinutes;
    private final ProcessExecutor processExecutor;
    private final ObjectMapper objectMapper;
    private final TikTokIngestionProperties properties;
    private final TikTokChallengeDetector challengeDetector;
    private final TikTokVideoIdentityResolver identityResolver;
    private final TikTokEmbedFallbackClient fallbackClient;

    public TikTokIngestionService(
        @Value("${app.ffmpeg.location}") String ffmpegLocation,
        @Value("${app.ytdlp.location:}") String ytDlpLocation,
        @Value("${app.ytdlp.timeout-minutes}") int ytDlpTimeoutMinutes,
        ProcessExecutor processExecutor,
        ObjectMapper objectMapper,
        TikTokIngestionProperties properties,
        TikTokChallengeDetector challengeDetector,
        TikTokVideoIdentityResolver identityResolver,
        TikTokEmbedFallbackClient fallbackClient
    ) {
        this.ffmpegLocation = ffmpegLocation;
        this.ytDlpLocation = ytDlpLocation == null || ytDlpLocation.isBlank() ? "yt-dlp" : ytDlpLocation;
        this.ytDlpTimeoutMinutes = ytDlpTimeoutMinutes;
        this.processExecutor = processExecutor;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.challengeDetector = challengeDetector;
        this.identityResolver = identityResolver;
        this.fallbackClient = fallbackClient;
    }

    public ProcessedVideoFiles ingest(String submittedUrl, Consumer<VideoMetadata> metadataValidator) throws Exception {
        String videoId = identityResolver.resolve(submittedUrl);
        Path temporaryDirectory = createTemporaryDirectory();
        try {
            try {
                return primary(submittedUrl, temporaryDirectory);
            } catch (ProcessExecutionException failure) {
                if (failure.isTimedOut() || !challengeDetector.isChallenge(failure)) {
                    throw failure;
                }
                if (!properties.getEmbedFallback().isEnabled()) {
                    throw new VideoProcessingException("TikTok is temporarily unavailable. Please try again later.", failure);
                }
                return fallback(videoId, temporaryDirectory, metadataValidator);
            }
        } catch (Exception e) {
            FileUtils.deleteRecursively(temporaryDirectory);
            throw e;
        }
    }

    public String resolveVideoId(String submittedUrl) {
        return identityResolver.resolve(submittedUrl);
    }

    private ProcessedVideoFiles primary(String submittedUrl, Path temporaryDirectory) throws Exception {
        String baseName = temporaryDirectory.resolve("output").toString();
        List<String> command = new ArrayList<>();
        command.add(ytDlpLocation);
        if (ffmpegLocation != null && !ffmpegLocation.isBlank() && new File(ffmpegLocation).exists()) {
            command.add("--ffmpeg-location");
            command.add(ffmpegLocation);
        }
        command.add("-f");
        command.add("worst[vcodec^=h264][acodec!=none]");
        command.add("-x");
        command.add("--audio-format");
        command.add("mp3");
        command.add("--audio-quality");
        command.add("5");
        command.add("--write-info-json");
        command.add("-o");
        command.add(baseName + ".%(ext)s");
        command.add("--");
        command.add(submittedUrl);
        processExecutor.run(ytDlpTimeoutMinutes, command.toArray(new String[0]));
        VideoMetadata metadata = objectMapper.readValue(new File(baseName + ".info.json"), VideoMetadata.class);
        return new ProcessedVideoFiles(new File(baseName + ".mp3"), new File(baseName + ".info.json"), temporaryDirectory, metadata);
    }

    private ProcessedVideoFiles fallback(String videoId, Path temporaryDirectory,
                                         Consumer<VideoMetadata> metadataValidator) throws Exception {
        TikTokEmbedMedia embed = fallbackClient.fetch(videoId);
        metadataValidator.accept(embed.getMetadata());
        Path media = fallbackClient.download(embed, temporaryDirectory);
        File audio = temporaryDirectory.resolve("output.mp3").toFile();
        String ffmpeg = ffmpegLocation == null || ffmpegLocation.isBlank() ? "ffmpeg" : ffmpegLocation;
        processExecutor.run(ytDlpTimeoutMinutes, ffmpeg, "-i", media.toString(), "-vn", "-q:a", "5", audio.toString());
        return new ProcessedVideoFiles(audio, null, temporaryDirectory, embed.getMetadata());
    }

    private Path createTemporaryDirectory() throws Exception {
        return Files.createTempDirectory("media-" + UUID.randomUUID());
    }
}
