package com.app.categorise.api.controller;

import com.app.categorise.api.dto.AddLinkRequest;
import com.app.categorise.domain.service.UntranscribedLinkService;
import com.app.categorise.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
     * Add a new untranscribed link for the authenticated user.
     */
    @PostMapping
    public ResponseEntity<Void> addLink(
            @Valid @RequestBody AddLinkRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getId();
        service.saveLink(userId, request.getLink());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Get all untranscribed links for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<String>> getLinks(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getId();
        return ResponseEntity.ok(service.getLinks(userId));
    }
}
