package com.app.categorise.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating user notes on a transcript.
 * Notes can be null to clear existing notes.
 */
public record UpdateNotesRequest(
    @Size(max = 10000, message = "Notes cannot exceed 10000 characters")
    String notes
) {}
