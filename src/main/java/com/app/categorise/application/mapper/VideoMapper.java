package com.app.categorise.application.mapper;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.dto.TikTokMetadata;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.domain.model.Transcript;
import com.app.categorise.domain.service.CategoryAliasService;
import com.app.categorise.data.entity.CategoryAliasEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapper class for video-related entity mappings.
 * Handles conversion between video data, metadata, and various entity types.
 */
@Component
public class VideoMapper {

    private final TranscriptMapper transcriptMapper;
    private final CategoryAliasService categoryAliasService;

    public VideoMapper(TranscriptMapper transcriptMapper, CategoryAliasService categoryAliasService) {
        this.transcriptMapper = transcriptMapper;
        this.categoryAliasService = categoryAliasService;
    }

    /**
     * Creates a BaseTranscriptEntity from video URL, transcript text, and metadata
     */
    public BaseTranscriptEntity createBaseTranscriptEntity(String videoUrl, String transcriptText, TikTokMetadata metadata) {
        return new BaseTranscriptEntity(
            videoUrl,
            transcriptText,
            metadata.getDescription(),
            metadata.getTitle(),
            (double) metadata.getDuration(),
            Instant.ofEpochSecond(metadata.getUploadedEpoch()),
            metadata.getAccountId(),
            metadata.getAccount(),
            metadata.getIdentifierId(),
            metadata.getIdentifier()
        );
    }

    /**
     * Creates a TranscriptEntity from video data and metadata (legacy method)
     */
    public TranscriptEntity createTranscriptEntity(String videoUrl, String transcriptText, TikTokMetadata metadata, UUID userId, UUID categoryId) {
        TranscriptEntity entity = new TranscriptEntity();
        entity.setVideoUrl(videoUrl);
        entity.setTranscript(transcriptText);
        entity.setDescription(metadata.getDescription());
        entity.setTitle(metadata.getTitle());
        entity.setDuration(metadata.getDuration());
        entity.setUploadedAt(Instant.ofEpochSecond(metadata.getUploadedEpoch()));
        entity.setAccountId(metadata.getAccountId());
        entity.setAccount(metadata.getAccount());
        entity.setIdentifierId(metadata.getIdentifierId());
        entity.setIdentifier(metadata.getIdentifier());
        entity.setUserId(userId);
        entity.setCategoryId(categoryId);
        return entity;
    }

    /**
     * Creates a UserTranscriptEntity linking a user to a base transcript with category
     */
    public UserTranscriptEntity createUserTranscriptEntity(UUID userId, BaseTranscriptEntity baseTranscript, CategoryEntity category) {
        UserTranscriptEntity userTranscript = new UserTranscriptEntity();
        userTranscript.setUserId(userId);
        userTranscript.setBaseTranscript(baseTranscript);
        userTranscript.setCategory(category);
        return userTranscript;
    }

    /**
     * Builds response DTO from BaseTranscriptEntity, UserTranscriptEntity, category name, and alias
     */
    public TranscriptDtoWithAliases buildResponse(BaseTranscriptEntity baseTranscript, UserTranscriptEntity userTranscript, 
                                                  String categoryName, String alias) {
        // Create a temporary Transcript domain object for mapping
        // TODO: This is a temporary solution - we should update the mapper to work with the new entities directly
        Transcript transcript = new Transcript(
            userTranscript.getId(),
            baseTranscript.getVideoUrl(),
            baseTranscript.getTranscript(),
            baseTranscript.getDescription(),
            baseTranscript.getTitle(),
            baseTranscript.getDuration() != null ? baseTranscript.getDuration() : 0.0,
            baseTranscript.getUploadedAt(),
            baseTranscript.getAccountId(),
            baseTranscript.getAccount(),
            baseTranscript.getIdentifierId(),
            baseTranscript.getIdentifier(),
            userTranscript.getCategoryId(),
            userTranscript.getUserId(),
            userTranscript.getCreatedAt()
        );
        
        return transcriptMapper.toDto(transcript, categoryName, alias);
    }

    /**
     * Builds response DTO from BaseTranscriptEntity and UserTranscriptEntity (for existing user transcripts)
     */
    public TranscriptDtoWithAliases buildResponse(BaseTranscriptEntity baseTranscript, UserTranscriptEntity userTranscript) {
        // For existing user transcripts, we need to get the category and alias
        CategoryEntity category = userTranscript.getCategory();
        String categoryName = category != null ? category.getName() : null;
        
        String alias = null;
        if (category != null) {
            alias = categoryAliasService.findByUserIdAndCategoryId(userTranscript.getUserId(), category.getId())
                .map(CategoryAliasEntity::getAlias)
                .orElse(null);
        }
        
        return buildResponse(baseTranscript, userTranscript, categoryName, alias);
    }
}