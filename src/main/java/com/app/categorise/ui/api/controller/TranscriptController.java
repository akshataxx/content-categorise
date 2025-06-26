package com.app.categorise.ui.api.controller;

import com.app.categorise.application.mapper.TranscriptMapper;
import com.app.categorise.application.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.domain.service.CategoryAliasService;
import com.app.categorise.domain.service.TranscriptService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transcript")
public class TranscriptController {

    private final TranscriptService transcriptService;
    private final CategoryAliasService categoryAliasService;
    private final TranscriptMapper transcriptMapper;

    public TranscriptController(TranscriptService transcriptService,
                                CategoryAliasService categoryAliasService,
                                TranscriptMapper transcriptMapper) {
        this.transcriptService = transcriptService;
        this.categoryAliasService = categoryAliasService;
        this.transcriptMapper = transcriptMapper;
    }


    /**
     * Find transcripts based on various filters.
     * Returns transcript + aliased categories.
     * Calls TranscriptService to get the data
     * Calls CategoryAliasService to get the user's aliases
     * Uses TranscriptMapper to build the final response
     * @param id
     * @param categories
     * @param account
     * @param from
     * @param to
     * @param userId
     * @return List of TranscriptDtoWithAliases
     */

    @GetMapping
    public List<TranscriptDtoWithAliases> findTranscripts(
            @RequestParam(required = false) String id,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String userId
    ) {
        List<TranscriptEntity> transcriptEntities;
        if (id != null && !id.isEmpty()) {
            transcriptEntities = transcriptService.findTranscript(id)
                    .map(List::of)
                    .orElse(List.of());
        }else{
            transcriptEntities = transcriptService.filterTranscripts(categories,account,from,to );
        }

        // Fetch alias map (canonical -> alias) for the user
        Map<String, String> aliasMap = userId != null
                ? categoryAliasService.getAliasesForUser(userId)
                : Map.of();

        return transcriptEntities.stream()
                .map(t -> transcriptMapper.toDto(t, t.getGroupingKey()))
                .toList();
    }
}
