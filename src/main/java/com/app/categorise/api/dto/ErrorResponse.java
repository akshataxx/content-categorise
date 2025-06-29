package com.app.categorise.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Standard structure for API error responses")
public class ErrorResponse {

    @Schema(description = "Timestamp of when the error occurred", example = "2025-06-15T06:30:00Z")
    private final Instant timestamp = Instant.now();

    @Schema(description = "HTTP status code", example = "400")
    private final int status;

    @Schema(description = "Short description of the HTTP error", example = "Bad Request")
    private final String error;

    @Schema(description = "Detailed error message", example = "Missing 'videoUrl' in request body")
    private final String message;

    @Schema(description = "Request path where the error occurred", example = "/video/url")
    private final String path;

    public ErrorResponse(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }
}
