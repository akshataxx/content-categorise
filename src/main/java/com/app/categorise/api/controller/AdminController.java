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
import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<ResponseEntity<Map<String, Object>>> backfillEmbeddings() {
        log.info("POST /api/admin/backfill-embeddings triggered");
        return videoService.backfillEmbeddings()
            .thenApply(counts -> ResponseEntity.ok(Map.of(
                "success", counts[0],
                "failed", counts[1]
            )));
    }
}
