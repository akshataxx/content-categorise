package com.app.categorise.application.mapper;

import com.app.categorise.api.dto.JobStatusDto;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.TranscriptionJobEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import org.springframework.stereotype.Component;

/**
 * Maps TranscriptionJobEntity to JobStatusDto.
 * Follows the same @Component pattern as VideoMapper.
 */
@Component
public class JobMapper {

    public JobStatusDto toDto(TranscriptionJobEntity entity) {
        String title = null;
        UserTranscriptEntity ut = entity.getUserTranscript();
        if (ut != null) {
            BaseTranscriptEntity bt = ut.getBaseTranscript();
            if (bt != null) {
                title = bt.getTitle();
            }
        }

        return new JobStatusDto(
            entity.getId(),
            entity.getVideoUrl(),
            entity.getStatus().name(),
            entity.getErrorMessage(),
            entity.getRetryCount(),
            entity.getUpdatedAt(),
            entity.getBaseTranscriptId(),
            entity.getUserTranscriptId(),
            title,
            entity.getPlatform()
        );
    }
}
