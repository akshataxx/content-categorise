package com.app.categorise.application.mapper;


import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.TranscriptEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper class to convert {@link TranscriptEntity} objects to {@link TranscriptDtoWithAliases} objects.
 */
@Component
public class TranscriptMapper {

    /**
     * Converts a TranscriptEntity to a TranscriptDtoWithAliases.
     * @param transcriptEntity The entity from the database.
     * @param categoryAlias The categoryAlias for the categoryId determined by the service layer.
     * @return A DTO ready to be sent to the client.
     */
    public TranscriptDtoWithAliases toDto(TranscriptEntity transcriptEntity, String category, String categoryAlias) {
        return new TranscriptDtoWithAliases(
            transcriptEntity.getId(),
            transcriptEntity.getVideoUrl(),
            transcriptEntity.getTranscript(),
            transcriptEntity.getDescription(),
            transcriptEntity.getTitle(),
            transcriptEntity.getDuration(),
            transcriptEntity.getUploadedAt(),
            transcriptEntity.getAccountId(),
            transcriptEntity.getAccount(),
            transcriptEntity.getIdentifierId(),
            transcriptEntity.getIdentifier(),
            categoryAlias,
            transcriptEntity.getCategoryId(),
            category,
            transcriptEntity.getCreatedAt()
        );
    }
}
