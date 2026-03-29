package com.app.categorise.application.internal;

import com.app.categorise.util.FileUtils;

import java.io.File;
import java.nio.file.Path;

/**
 * Represents processed video files, including audio and metadata files.
 * On close(), the entire temp directory is deleted recursively, cleaning up
 * all yt-dlp output regardless of container format (.mp4, .webm, .mkv, etc.).
 */
public class ProcessedVideoFiles implements AutoCloseable {
    private final File audioFile;
    private final File metadataFile;
    private final Path tempDir;

    public ProcessedVideoFiles(File audioFile, File metadataFile, Path tempDir) {
        this.audioFile = audioFile;
        this.metadataFile = metadataFile;
        this.tempDir = tempDir;
    }

    public File getAudioFile() {
        return audioFile;
    }

    public File getMetadataFile() {
        return metadataFile;
    }

    @Override
    public void close() {
        if (tempDir != null) {
            FileUtils.deleteRecursively(tempDir);
        }
    }
}
