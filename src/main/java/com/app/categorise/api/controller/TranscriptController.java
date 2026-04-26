package com.app.categorise.api.controller;

import com.app.categorise.api.dto.DeleteTranscriptsRequest;
import com.app.categorise.api.dto.SetSubcategoryRequest;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.api.dto.UpdateNotesRequest;
import com.app.categorise.domain.service.TranscriptService;
import com.app.categorise.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping({"/transcript", "/api/v1/transcripts"})
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
     * @param principal The authenticated user principal
     * @return A list of {@link TranscriptDtoWithAliases} matching the applied filters
     */
    @GetMapping
    public ResponseEntity<List<TranscriptDtoWithAliases>>  findTranscripts(
        @RequestParam(required = false) List<UUID> categoryIds,
        @RequestParam(required = false) List<UUID> subcategoryIds,
        @RequestParam(required = false) String account,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Authentication authentication
    ) {
        UUID userId = requireUser(authentication);
        System.out.printf("Finding all transcripts with filters: categoryIds=%s, subcategoryIds=%s, account=%s, from=%s, to=%s, userId=%s%n",
            categoryIds, subcategoryIds, account, from, to, userId
        );

        List<TranscriptDtoWithAliases> results = transcriptService.allFilteredTranscripts(userId, categoryIds, subcategoryIds, account, from, to);

        return ResponseEntity.ok(results);
    }

    /**
     * Find user transcript based on user transcript id
     * @param userTranscriptId The user transcript ID to find
     * @param principal The authenticated user principal
     * @return A {@link TranscriptDtoWithAliases} matching the id
     */
    @GetMapping("/{userTranscriptId}")
    public ResponseEntity<TranscriptDtoWithAliases> findTranscript(
        @PathVariable UUID userTranscriptId,
        Authentication authentication
    ) {
        UUID userId = requireUser(authentication);
        return transcriptService.findTranscript(userTranscriptId, userId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Update notes for a user transcript.
     * @param userTranscriptId The user transcript ID to update
     * @param request The request containing the notes content
     * @param principal The authenticated user principal
     * @return ResponseEntity with no content on successful update
     */
    @PatchMapping("/{userTranscriptId}/notes")
    public ResponseEntity<Void> updateNotes(
        @PathVariable UUID userTranscriptId,
        @RequestBody @Valid UpdateNotesRequest request,
        Authentication authentication
    ) {
        UUID userId = requireUser(authentication);
        logger.info("Updating notes for user transcript: {}", userTranscriptId);
        transcriptService.updateNotes(userId, userTranscriptId, request.notes());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userTranscriptId}/subcategory")
    public ResponseEntity<TranscriptDtoWithAliases> setSubcategory(
        @PathVariable UUID userTranscriptId,
        @RequestBody SetSubcategoryRequest request,
        Authentication authentication
    ) {
        UUID userId = requireUser(authentication);
        TranscriptDtoWithAliases updated = transcriptService.setSubcategory(
            userId,
            userTranscriptId,
            request.subcategoryId()
        );
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete multiple user transcripts by their IDs.
     * @param request The request containing the list of user transcript IDs to delete
     * @param principal The authenticated user principal
     * @return ResponseEntity with no content on successful deletion
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteTranscripts(
        @Valid @RequestBody DeleteTranscriptsRequest request,
        Authentication authentication
    ) {
        UUID userId = requireUser(authentication);
        logger.info("Received request to delete user transcripts: {}", request.getTranscriptIds());

        logger.info("Deleting {} user transcripts for user {}", request.getTranscriptIds().size(), userId);
        transcriptService.deleteTranscripts(userId, request.getTranscriptIds());

        logger.info("Successfully deleted {} user transcripts for user {}", request.getTranscriptIds().size(), userId);
        return ResponseEntity.noContent().build();
    }

    private UUID requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalArgumentException("User not authenticated");
        }
        if (principal == null || principal.getId() == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        return principal.getId();
    }
}
