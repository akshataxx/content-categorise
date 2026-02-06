package com.app.categorise.application.internal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents processed video files, including audio and metadata files.
 */
public class ProcessedVideoFiles implements AutoCloseable {
    private final File audioFile;
    private final File metadataFile;
    private final File videoFile;
    private final Path tempDir;

    public ProcessedVideoFiles(File audioFile, File metadataFile, File videoFile, Path tempDir) {
        this.audioFile = audioFile;
        this.metadataFile = metadataFile;
        this.videoFile = videoFile;
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
        if (!audioFile.delete()) {
            System.err.println("Failed to delete audio file: " + audioFile.getAbsolutePath());
        }
        if (!metadataFile.delete()) {
            System.err.println("Failed to delete metadata file: " + metadataFile.getAbsolutePath());
        }
        // Clean up video file if it exists (leftover from yt-dlp)
        if (videoFile != null && videoFile.exists() && !videoFile.delete()) {
            System.err.println("Failed to delete video file: " + videoFile.getAbsolutePath());
        }
        // Clean up the temp directory
        if (tempDir != null) {
            try {
                Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                System.err.println("Failed to delete temp directory: " + tempDir);
            }
        }
    }
}
