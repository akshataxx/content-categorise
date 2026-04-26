package com.app.categorise.api.dto;

import java.util.List;
import java.util.UUID;

public record CategoryWithSubcategoriesDto(
    UUID id,
    String name,
    String description,
    List<UserSubcategoryDto> subcategories
) {}
