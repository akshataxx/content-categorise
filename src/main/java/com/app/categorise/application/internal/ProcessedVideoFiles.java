package com.app.categorise.application.internal;

import com.app.categorise.data.dto.VideoMetadata;
import com.app.categorise.util.FileUtils;

import java.io.File;
import java.nio.file.Path;

/**
 * Represents processed video files. TikTok writes metadata in the same yt-dlp
 * invocation as the audio download to avoid a second challenge request.
 * On close(), the entire temp directory is deleted recursively, cleaning up
 * all yt-dlp output regardless of container format (.mp4, .webm, .mkv, etc.).
 */
public class ProcessedVideoFiles implements AutoCloseable {
    private final File audioFile;
    private final File metadataFile;
    private final Path tempDir;
    private final VideoMetadata metadata;

    public ProcessedVideoFiles(File audioFile, File metadataFile, Path tempDir) {
        this(audioFile, metadataFile, tempDir, null);
    }

    public ProcessedVideoFiles(File audioFile, File metadataFile, Path tempDir, VideoMetadata metadata) {
        this.audioFile = audioFile;
        this.metadataFile = metadataFile;
        this.tempDir = tempDir;
        this.metadata = metadata;
    }

    public File getAudioFile() {
        return audioFile;
    }

    public File getMetadataFile() {
        return metadataFile;
    }

    public VideoMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() {
        if (tempDir != null) {
            FileUtils.deleteRecursively(tempDir);
        }
    }
}
