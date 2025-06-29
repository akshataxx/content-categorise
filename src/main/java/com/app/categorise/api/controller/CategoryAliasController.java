package com.app.categorise.api.controller;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.CategoryAliasEntity;
import com.app.categorise.domain.service.CategoryAliasService;
import com.app.categorise.api.dto.RenameAliasRequest;
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

    @PutMapping("/upsert")
    @Operation(summary = "Upsert a categoryId alias for a user", description = "Updates the alias for a given canonical categoryId.")
    public ResponseEntity<CategoryAliasEntity> upsertAlias(@RequestBody RenameAliasRequest request) throws Exception {
        CategoryAliasEntity alias = aliasService.upsertAlias(
            request.getUserId(),
            request.getCategoryId(),
            request.getNewAlias()
        );
        return ResponseEntity.ok(alias);
    }
}