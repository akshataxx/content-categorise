package com.app.categorise.api.controller;

import com.app.categorise.api.dto.AddLinkRequest;
import com.app.categorise.domain.service.UntranscribedLinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/untranscribedLink")
public class UntranscribedLinkController {

    private final UntranscribedLinkService service;

    public UntranscribedLinkController(UntranscribedLinkService service) {
        this.service = service;
    }

    /**
     * Add a new untranscribed link for a user.
     */
    @PostMapping
    public ResponseEntity<Void> addLink(@Valid @RequestBody AddLinkRequest request) {
        service.saveLink(request.getUserId(), request.getLink());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Get all untranscribed links for the given user.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<String>> getLinks(@PathVariable UUID userId) {
        return ResponseEntity.ok(service.getLinks(userId));
    }
}
