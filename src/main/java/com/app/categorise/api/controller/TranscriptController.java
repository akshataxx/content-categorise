package com.app.categorise.api.controller;

import com.app.categorise.application.mapper.TranscriptMapper;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.CategoryAliasEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.domain.model.Transcript;
import com.app.categorise.domain.service.CategoryAliasService;
import com.app.categorise.domain.service.CategoryService;
import com.app.categorise.domain.service.TranscriptService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/transcript")
public class TranscriptController {

    private final TranscriptService transcriptService;
    private final CategoryAliasService categoryAliasService;
    private final CategoryService categoryService;
    private final TranscriptMapper transcriptMapper;

    public TranscriptController(TranscriptService transcriptService,
        CategoryAliasService categoryAliasService,
        CategoryService categoryService,
        TranscriptMapper transcriptMapper
    ) {
        this.transcriptService = transcriptService;
        this.categoryAliasService = categoryAliasService;
        this.categoryService = categoryService;
        this.transcriptMapper = transcriptMapper;
    }

    /**
     * Find transcripts based on various filters.
     * Returns transcript + aliased categories.
     * Calls TranscriptService to get the data
     * Calls CategoryAliasService to get the user's aliases
     * Uses TranscriptMapper to build the final response
     * @param categoryIds Optional list of category IDs to filter transcripts
     * @param account Optional account name to filter transcripts
     * @param from Optional start of date-time range (ISO 8601 format)
     * @param to Optional end of date-time range (ISO 8601 format)
     * @param userId Optional user ID used to fetch category aliases for personalization
     * @return A list of {@link TranscriptDtoWithAliases} matching the applied filters
     */
    @GetMapping
    public ResponseEntity<List<TranscriptDtoWithAliases>>  findTranscripts(
        @RequestParam(required = false) List<UUID> categoryIds,
        @RequestParam(required = false) String account,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) UUID userId
    ) {
        System.out.printf("Finding all transcripts with filters: categoryIds=%s, account=%s, from=%s, to=%s%n",
            categoryIds, account, from, to
        );

        List<Transcript> transcripts = transcriptService.allFilteredTranscripts(categoryIds, account, from, to);

        List<TranscriptDtoWithAliases> results = transcripts.stream()
            .map(transcript -> mapToDtoWithAlias(transcript, userId))
            .toList();

        return ResponseEntity.ok(results);
    }

    /**
     * Find transcripts based on id
     * @param userId Optional user ID used to fetch category aliases for personalization
     * @return A {@link TranscriptDtoWithAliases} matching the id
     */
    @GetMapping("/{transcriptId}")
    public ResponseEntity<TranscriptDtoWithAliases> findTranscript(
        @PathVariable UUID transcriptId,
        @RequestParam(required = false) UUID userId
    ) {
        return transcriptService.findTranscript(transcriptId)
            .map(transcript -> mapToDtoWithAlias(transcript, userId))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private TranscriptDtoWithAliases mapToDtoWithAlias(Transcript transcript, UUID userId) {
        String alias = null;
        if (userId != null) {
            alias = categoryAliasService.findByUserIdAndCategoryId(userId, transcript.getCategoryId())
                .map(CategoryAliasEntity::getAlias).orElse(null);
        }

        Optional<CategoryEntity> category = categoryService.findCategoryById(transcript.getCategoryId());
        String categoryName = category.map(CategoryEntity::getName).orElse(null);

        return transcriptMapper.toDto(transcript, categoryName, alias);
    }
}

