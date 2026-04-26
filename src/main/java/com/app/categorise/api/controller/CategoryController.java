package com.app.categorise.api.controller;

import com.app.categorise.api.dto.CategoryWithSubcategoriesDto;
import com.app.categorise.api.dto.CreateSubcategoryRequest;
import com.app.categorise.api.dto.UpdateSubcategoryRequest;
import com.app.categorise.api.dto.UserSubcategoryDto;
import com.app.categorise.application.mapper.UserSubcategoryMapper;
import com.app.categorise.data.entity.UserSubcategoryEntity;
import com.app.categorise.domain.service.CategoryService;
import com.app.categorise.domain.service.UserSubcategoryService;
import com.app.categorise.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;
    private final UserSubcategoryMapper userSubcategoryMapper;
    private final UserSubcategoryService userSubcategoryService;

    public CategoryController(CategoryService categoryService,
                              UserSubcategoryMapper userSubcategoryMapper,
                              UserSubcategoryService userSubcategoryService) {
        this.categoryService = categoryService;
        this.userSubcategoryMapper = userSubcategoryMapper;
        this.userSubcategoryService = userSubcategoryService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryWithSubcategoriesDto>> findCategories(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        Map<UUID, List<UserSubcategoryDto>> subcategoriesByParent = userSubcategoryService.findByUser(userId).stream()
            .map(userSubcategoryMapper::toDto)
            .sorted(Comparator.comparing(UserSubcategoryDto::name, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.groupingBy(UserSubcategoryDto::parentId));

        List<CategoryWithSubcategoriesDto> response = categoryService.getAllRootCategories().stream()
            .map(category -> new CategoryWithSubcategoriesDto(
                category.getId(),
                category.getName(),
                category.getDescription(),
                subcategoriesByParent.getOrDefault(category.getId(), List.of())
            ))
            .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/categories/{rootId}/subcategories")
    public ResponseEntity<UserSubcategoryDto> createSubcategory(
        @PathVariable UUID rootId,
        @Valid @RequestBody CreateSubcategoryRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        UserSubcategoryEntity entity = userSubcategoryService.createSubcategory(
            userId,
            rootId,
            request.name(),
            request.description()
        );
        return ResponseEntity.ok(userSubcategoryMapper.toDto(entity));
    }

    @GetMapping("/categories/{rootId}/subcategories")
    public ResponseEntity<List<UserSubcategoryDto>> findSubcategories(
        @PathVariable UUID rootId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        List<UserSubcategoryDto> response = userSubcategoryService.findByUserAndParent(userId, rootId).stream()
            .map(userSubcategoryMapper::toDto)
            .sorted(Comparator.comparing(UserSubcategoryDto::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/subcategories/{id}")
    public ResponseEntity<UserSubcategoryDto> updateSubcategory(
        @PathVariable UUID id,
        @RequestBody UpdateSubcategoryRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        UserSubcategoryEntity entity = userSubcategoryService.updateSubcategory(
            userId,
            id,
            request.name(),
            request.description()
        );
        return ResponseEntity.ok(userSubcategoryMapper.toDto(entity));
    }

    @DeleteMapping("/subcategories/{id}")
    public ResponseEntity<Void> deleteSubcategory(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = requireUser(principal);
        userSubcategoryService.deleteSubcategory(userId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID requireUser(UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        return principal.getId();
    }
}
