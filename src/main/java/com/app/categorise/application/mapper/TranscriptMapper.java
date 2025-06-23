package com.app.categorise.application.mapper;


import com.app.categorise.application.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.TranscriptEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper class to convert Transcript entities to DTOs with category aliases.
 * Maps a TranscriptEntity + aliasMap -> TranscriptDtoWithAliases
 */
@Component
public class TranscriptMapper {

    public TranscriptDtoWithAliases toDtoWithAlias(TranscriptEntity transcriptEntity, Map<String, String> aliasMap) {
        TranscriptDtoWithAliases dto = new TranscriptDtoWithAliases();
        dto.setId(transcriptEntity.getId());
        dto.setVideoUrl(transcriptEntity.getVideoUrl());
        dto.setTranscript(transcriptEntity.getTranscript());
        dto.setDescription(transcriptEntity.getDescription());
        dto.setTitle(transcriptEntity.getTitle());
        dto.setDuration(transcriptEntity.getDuration());
        dto.setUploadedAt(transcriptEntity.getUploadedAt());
        dto.setAccountId(transcriptEntity.getAccountId());
        dto.setAccount(transcriptEntity.getAccount());
        dto.setIdentifierId(transcriptEntity.getIdentifierId());
        dto.setIdentifier(transcriptEntity.getIdentifier());

        // Replace categories with aliases where available
        List<String> aliasedCategories = transcriptEntity.getCategories().stream()
                .map(category -> aliasMap.getOrDefault(category, category))
                .collect(Collectors.toList());
        dto.setCategories(aliasedCategories);

        return dto;
    }
}
