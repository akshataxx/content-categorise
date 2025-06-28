package com.app.categorise.api.controller;

import com.app.categorise.application.mapper.TranscriptMapper;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.CategoryAliasEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.domain.service.CategoryAliasService;
import com.app.categorise.domain.service.CategoryService;
import com.app.categorise.domain.service.TranscriptService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
     * @param id
     * @param categoryIds
     * @param account
     * @param from
     * @param to
     * @param userId
     * @return List of TranscriptDtoWithAliases
     */

    @GetMapping
    public List<TranscriptDtoWithAliases> findTranscripts(
        @RequestParam(required = false) String id,
        @RequestParam(required = false) List<String> categoryIds,
        @RequestParam(required = false) String account,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String userId
    ) {
        if (id != null && !id.isEmpty()) {
            return transcriptService.findTranscript(id)
                .map(transcript -> List.of(mapToDtoWithAlias(transcript, userId)))
                .orElse(List.of());
        }

        List<TranscriptEntity> transcriptEntities = transcriptService.allFilteredTranscripts(categoryIds, account, from, to);
        return transcriptEntities.stream()
            .map(transcript -> mapToDtoWithAlias(transcript, userId))
            .toList();
    }

    private TranscriptDtoWithAliases mapToDtoWithAlias(TranscriptEntity transcript, String userId) {
        String alias = null;
        if (userId != null && !userId.isEmpty()) {
            alias = categoryAliasService.findByUserIdAndCategoryId(userId, transcript.getCategoryId())
                .map(CategoryAliasEntity::getAlias).orElse(null);
        }

        Optional<CategoryEntity> category = categoryService.findCategoryById(transcript.getCategoryId());
        String categoryName = category.map(CategoryEntity::getName).orElse(null);

        return transcriptMapper.toDto(transcript, categoryName, alias);
    }
}

