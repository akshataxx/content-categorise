package com.app.categorise.application.mapper;


import com.app.categorise.application.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.TranscriptEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper class to convert {@link TranscriptEntity} objects to {@link TranscriptDtoWithAliases} objects.
 */
@Component
public class TranscriptMapper {

    /**
     * Converts a TranscriptEntity to a TranscriptDtoWithAliases.
     * @param transcriptEntity The entity from the database.
     * @param groupingKey The grouping key determined by the service layer.
     * @return A DTO ready to be sent to the client.
     */
    public TranscriptDtoWithAliases toDto(TranscriptEntity transcriptEntity, String groupingKey) {
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
        dto.setAlias(transcriptEntity.getAlias());
        dto.setCanonicalCategory(transcriptEntity.getCanonicalCategory());
        dto.setGroupingKey(groupingKey);
        dto.setCreatedAt(transcriptEntity.getCreatedAt());

        return dto;
    }
}
