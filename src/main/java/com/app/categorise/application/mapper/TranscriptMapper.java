package com.app.categorise.application.mapper;


import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.domain.model.Transcript;
import org.springframework.stereotype.Component;

/**
 * Mapper class to convert {@link TranscriptEntity} objects to {@link TranscriptDtoWithAliases} objects.
 */
@Component
public class TranscriptMapper {

    /**
     * Converts a TranscriptEntity to a TranscriptDtoWithAliases.
     * @param transcript The entity from the database.
     * @param categoryAlias The categoryAlias for the categoryId determined by the service layer.
     * @return A DTO ready to be sent to the client.
     */
    // Domain to dto
    public TranscriptDtoWithAliases toDto(Transcript transcript, String category, String categoryAlias) {
        return new TranscriptDtoWithAliases(
            transcript.getId(),
            transcript.getVideoUrl(),
            transcript.getTranscript(),
            transcript.getDescription(),
            transcript.getTitle(),
            transcript.getDuration(),
            transcript.getUploadedAt(),
            transcript.getAccountId(),
            transcript.getAccount(),
            transcript.getIdentifierId(),
            transcript.getIdentifier(),
            categoryAlias,
            transcript.getCategoryId(),
            category,
            transcript.getCreatedAt()
        );
    }

    // Entity to domain
    public Transcript toDomain(TranscriptEntity entity) {
        return new Transcript(
            entity.getId(),
            entity.getVideoUrl(),
            entity.getTranscript(),
            entity.getDescription(),
            entity.getTitle(),
            entity.getDuration(),
            entity.getUploadedAt(),
            entity.getAccountId(),
            entity.getAccount(),
            entity.getIdentifierId(),
            entity.getIdentifier(),
            entity.getCategoryId(),
            entity.getUserId(),
            entity.getCreatedAt()
        );
    }

    // Domain to entity
    public TranscriptEntity toEntity(Transcript domain) {
        TranscriptEntity entity = new TranscriptEntity();
        entity.setVideoUrl(domain.getVideoUrl());
        entity.setTranscript(domain.getTranscript());
        entity.setDescription(domain.getDescription());
        entity.setTitle(domain.getTitle());
        entity.setDuration(domain.getDuration());
        entity.setUploadedAt(domain.getUploadedAt());
        entity.setAccountId(domain.getAccountId());
        entity.setAccount(domain.getAccount());
        entity.setIdentifierId(domain.getIdentifierId());
        entity.setIdentifier(domain.getIdentifier());
        entity.setCategoryId(domain.getCategoryId());
        entity.setUserId(domain.getUserId());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
