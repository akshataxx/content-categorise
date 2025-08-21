package com.app.categorise.api.controller;

import com.app.categorise.api.dto.DeleteTranscriptsRequest;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.domain.service.TranscriptService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transcript")
public class TranscriptController {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptController.class);

    private final TranscriptService transcriptService;

    public TranscriptController(TranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }

    /**
     * Find user transcripts based on various filters.
     * Returns transcript + aliased categories.
     * Calls TranscriptService to get the data with user-specific filtering
     * @param categoryIds Optional list of category IDs to filter transcripts
     * @param account Optional account name to filter transcripts
     * @param from Optional start of date-time range (ISO 8601 format)
     * @param to Optional end of date-time range (ISO 8601 format)
     * @param userId Required user ID to fetch user's transcripts
     * @return A list of {@link TranscriptDtoWithAliases} matching the applied filters
     */
    @GetMapping
    public ResponseEntity<List<TranscriptDtoWithAliases>>  findTranscripts(
        @RequestParam(required = false) List<UUID> categoryIds,
        @RequestParam(required = false) String account,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = true) UUID userId
    ) {
        System.out.printf("Finding all transcripts with filters: categoryIds=%s, account=%s, from=%s, to=%s, userId=%s%n",
            categoryIds, account, from, to, userId
        );

        List<TranscriptDtoWithAliases> results = transcriptService.allFilteredTranscripts(userId, categoryIds, account, from, to);

        return ResponseEntity.ok(results);
    }

    /**
     * Find user transcript based on user transcript id
     * @param userTranscriptId The user transcript ID to find
     * @param userId Required user ID for authorization and personalization
     * @return A {@link TranscriptDtoWithAliases} matching the id
     */
    @GetMapping("/{userTranscriptId}")
    public ResponseEntity<TranscriptDtoWithAliases> findTranscript(
        @PathVariable UUID userTranscriptId,
        @RequestParam(required = true) UUID userId
    ) {
        return transcriptService.findTranscript(userTranscriptId, userId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Delete multiple user transcripts by their IDs.
     * @param request The request containing the list of user transcript IDs to delete
     * @return ResponseEntity with no content on successful deletion
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteTranscripts(@Valid @RequestBody DeleteTranscriptsRequest request) {
        logger.info("Received request to delete user transcripts: {}", request.getTranscriptIds());
        
        validateDeleteTranscriptsRequest(request);
        
        logger.info("Deleting {} user transcripts", request.getTranscriptIds().size());
        transcriptService.deleteTranscripts(request.getTranscriptIds());
        
        logger.info("Successfully deleted {} user transcripts", request.getTranscriptIds().size());
        return ResponseEntity.noContent().build();
    }

    private void validateDeleteTranscriptsRequest(DeleteTranscriptsRequest request) {
        // Validate request
        if (request == null) {
            logger.warn("Delete user transcripts request is null");
            throw new IllegalArgumentException("Request body cannot be null");
        }
        
        if (request.getTranscriptIds() == null || request.getTranscriptIds().isEmpty()) {
            logger.warn("Delete user transcripts request contains null or empty transcript IDs list");
            throw new IllegalArgumentException("User transcript IDs list cannot be null or empty");
        }

        // Validate individual IDs
        if (request.getTranscriptIds().stream().anyMatch(id -> id == null)) {
            logger.warn("Delete user transcripts request contains null transcript ID");
            throw new IllegalArgumentException("User transcript IDs cannot contain null values");
        }

        // Check for reasonable batch size (prevent potential DoS)
        if (request.getTranscriptIds().size() > 100) {
            logger.warn("Delete user transcripts request contains too many IDs: {}", request.getTranscriptIds().size());
            throw new IllegalArgumentException("Cannot delete more than 100 user transcripts at once");
        }
    }
}

