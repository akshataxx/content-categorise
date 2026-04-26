package com.app.categorise.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UserSubcategoryDto(
    UUID id,
    UUID parentId,
    String name,
    String description,
    Instant createdAt
) {}
