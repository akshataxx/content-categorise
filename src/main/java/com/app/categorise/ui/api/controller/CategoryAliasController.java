package com.app.categorise.ui.api.controller;

import com.app.categorise.domain.service.CategorisationService;
import com.app.categorise.domain.service.CategoryAliasService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/category-alias")
public class CategoryAliasController {

    private final CategorisationService categorisationService;
    private final CategoryAliasService categoryAliasService;

    public CategoryAliasController(CategorisationService categorisationService,
                                   CategoryAliasService categoryAliasService) {
        this.categorisationService = categorisationService;
        this.categoryAliasService = categoryAliasService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Void> generateAndSaveAliases(
            @RequestParam String userId,
            @RequestBody List<String> canonicalCategories
    ) {
        Map<String, String> aliases = categorisationService.generateAliases(canonicalCategories);
        categoryAliasService.saveAliases(userId, aliases);
        return ResponseEntity.ok().build();
    }
}