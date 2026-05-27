package com.app.categorise.api.dto;

import java.util.List;

public record TranscriptPageResponse(
    List<TranscriptDtoWithAliases> items,
    int page,
    int size,
    long totalItems,
    int totalPages,
    boolean hasNext
) { }
