package com.app.categorise.exception;

import java.util.List;
import java.util.UUID;

public class TranscriptDeletionException extends RuntimeException {
    
    private final List<UUID> failedIds;

    public TranscriptDeletionException(String message) {
        super(message);
        this.failedIds = null;
    }

    public TranscriptDeletionException(String message, Throwable cause) {
        super(message, cause);
        this.failedIds = null;
    }

    public TranscriptDeletionException(String message, List<UUID> failedIds) {
        super(message);
        this.failedIds = failedIds;
    }

    public TranscriptDeletionException(String message, List<UUID> failedIds, Throwable cause) {
        super(message, cause);
        this.failedIds = failedIds;
    }

    public List<UUID> getFailedIds() {
        return failedIds;
    }
}