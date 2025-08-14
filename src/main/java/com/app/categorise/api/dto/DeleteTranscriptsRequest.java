package com.app.categorise.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Request to delete multiple transcripts")
public class DeleteTranscriptsRequest {
    
    @NotNull(message = "Transcript IDs list cannot be null")
    @NotEmpty(message = "Transcript IDs list cannot be empty")
    @Size(max = 100, message = "Cannot delete more than 100 transcripts at once")
    @Schema(description = "List of transcript IDs to delete", example = "[\"550e8400-e29b-41d4-a716-446655440001\", \"550e8400-e29b-41d4-a716-446655440002\"]")
    private List<UUID> transcriptIds;

    public DeleteTranscriptsRequest() {
    }

    public DeleteTranscriptsRequest(List<UUID> transcriptIds) {
        this.transcriptIds = transcriptIds;
    }

    public List<UUID> getTranscriptIds() {
        return transcriptIds;
    }

    public void setTranscriptIds(List<UUID> transcriptIds) {
        this.transcriptIds = transcriptIds;
    }
}