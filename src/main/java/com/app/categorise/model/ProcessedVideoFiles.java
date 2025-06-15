package com.app.categorise.model;

import java.io.File;

public class ProcessedVideoFiles implements AutoCloseable {
    private final File audioFile;
    private final File metadataFile;

    public ProcessedVideoFiles(File audioFile, File metadataFile) {
        this.audioFile = audioFile;
        this.metadataFile = metadataFile;
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
    }
}

