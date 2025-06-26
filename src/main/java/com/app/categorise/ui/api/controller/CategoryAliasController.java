package com.app.categorise.ui.api.controller;

import com.app.categorise.domain.service.CategoryAliasService;
import com.app.categorise.ui.api.dto.RenameAliasRequest;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing category alias operations.
 */
@RestController
@RequestMapping("/api/v1/aliases")
public class CategoryAliasController {

    private final CategoryAliasService aliasService;

    public CategoryAliasController(CategoryAliasService aliasService) {
        this.aliasService = aliasService;
    }

    @PutMapping("/rename")
    @Operation(summary = "Rename a category alias for a user", description = "Updates the alias for a given canonical category and updates all existing transcripts for that user.")
    public ResponseEntity<Void> renameAlias(@RequestBody RenameAliasRequest request) {
        aliasService.renameAlias(
                request.getUserId(),
                request.getGroupingKey(),
                request.getNewAlias()
        );
        return ResponseEntity.ok().build();
    }
}