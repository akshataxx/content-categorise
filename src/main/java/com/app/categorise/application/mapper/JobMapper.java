package com.app.categorise.application.mapper;

import com.app.categorise.api.dto.JobStatusDto;
import com.app.categorise.data.entity.TranscriptionJobEntity;
import org.springframework.stereotype.Component;

/**
 * Maps TranscriptionJobEntity to JobStatusDto.
 * Follows the same @Component pattern as VideoMapper.
 */
@Component
public class JobMapper {

    public JobStatusDto toDto(TranscriptionJobEntity entity) {
        return new JobStatusDto(
            entity.getId(),
            entity.getVideoUrl(),
            entity.getStatus().name(),
            entity.getErrorMessage(),
            entity.getRetryCount(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getBaseTranscriptId()
        );
    }
}
