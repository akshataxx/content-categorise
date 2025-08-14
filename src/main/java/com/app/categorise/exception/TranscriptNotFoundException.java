package com.app.categorise.exception;

import java.util.List;
import java.util.UUID;

public class TranscriptNotFoundException extends RuntimeException {
    
    private final List<UUID> notFoundIds;

    public TranscriptNotFoundException(String message) {
        super(message);
        this.notFoundIds = null;
    }

    public TranscriptNotFoundException(String message, List<UUID> notFoundIds) {
        super(message);
        this.notFoundIds = notFoundIds;
    }

    public List<UUID> getNotFoundIds() {
        return notFoundIds;
    }
}