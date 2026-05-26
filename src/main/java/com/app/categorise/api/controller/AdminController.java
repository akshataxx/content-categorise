package com.app.categorise.api.controller;

import com.app.categorise.domain.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Admin", description = "Administrative operations")
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final VideoService videoService;

    public AdminController(VideoService videoService) {
        this.videoService = videoService;
    }

    @Operation(summary = "Backfill embeddings", description = "Regenerates embeddings for all base transcripts using structured content")
    @PostMapping("/backfill-embeddings")
    public ResponseEntity<Map<String, Object>> backfillEmbeddings() {
        log.info("POST /api/admin/backfill-embeddings triggered");
        videoService.backfillEmbeddings();
        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    @Operation(summary = "Re-extract and re-embed", description = "Re-extracts structured content using updated prompts then regenerates embeddings for all base transcripts")
    @PostMapping("/reextract-and-reembed")
    public ResponseEntity<Map<String, Object>> reextractAndReembed() {
        log.info("POST /api/admin/reextract-and-reembed triggered");
        videoService.reextractAndReembedAll();
        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }
}
