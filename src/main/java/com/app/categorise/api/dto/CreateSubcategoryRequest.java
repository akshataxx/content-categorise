package com.app.categorise.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSubcategoryRequest(
    @NotBlank String name,
    String description
) {}
