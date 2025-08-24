package com.app.categorise.application.mapper;

import com.app.categorise.data.entity.UserRateLimitEntity;
import com.app.categorise.data.entity.UserRateLimitTrackingEntity;
import com.app.categorise.domain.model.RateLimitConfig;
import com.app.categorise.domain.model.RateLimitUsage;
import org.springframework.stereotype.Component;

/**
 * RateLimitMapper - Maps between domain models and JPA entities for rate limiting
 * Follows the project's pattern of explicit mapping between layers
 */
@Component
public class RateLimitMapper {

    /**
     * Convert UserRateLimitEntity to RateLimitConfig domain model
     * @param entity The JPA entity
     * @return The domain model
     */
    public RateLimitConfig toDomainModel(UserRateLimitEntity entity) {
        if (entity == null) {
            return null;
        }

        return new RateLimitConfig(
                entity.getId(),
                entity.getUserId(),
                entity.getTranscriptsPerMinuteLimit(),
                entity.getTranscriptsPerDayLimit(),
                entity.getTotalTranscriptsLimit(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Convert RateLimitConfig domain model to UserRateLimitEntity
     * @param domainModel The domain model
     * @return The JPA entity
     */
    public UserRateLimitEntity toEntity(RateLimitConfig domainModel) {
        if (domainModel == null) {
            return null;
        }

        return new UserRateLimitEntity(
                domainModel.getId(),
                domainModel.getUserId(),
                domainModel.getTranscriptsPerMinuteLimit(),
                domainModel.getTranscriptsPerDayLimit(),
                domainModel.getTotalTranscriptsLimit(),
                domainModel.getCreatedAt(),
                domainModel.getUpdatedAt()
        );
    }

    /**
     * Convert UserRateLimitTrackingEntity to RateLimitUsage domain model
     * @param entity The JPA entity
     * @return The domain model
     */
    public RateLimitUsage toDomainModel(UserRateLimitTrackingEntity entity) {
        if (entity == null) {
            return null;
        }

        // Convert entity WindowType to domain WindowType
        RateLimitUsage.WindowType domainWindowType = entity.getWindowType() == UserRateLimitTrackingEntity.WindowType.MINUTE
                ? RateLimitUsage.WindowType.MINUTE
                : RateLimitUsage.WindowType.DAY;

        return new RateLimitUsage(
                entity.getId(),
                entity.getUserId(),
                entity.getWindowStart(),
                domainWindowType,
                entity.getRequestCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Convert RateLimitUsage domain model to UserRateLimitTrackingEntity
     * @param domainModel The domain model
     * @return The JPA entity
     */
    public UserRateLimitTrackingEntity toEntity(RateLimitUsage domainModel) {
        if (domainModel == null) {
            return null;
        }

        // Convert domain WindowType to entity WindowType
        UserRateLimitTrackingEntity.WindowType entityWindowType = domainModel.getWindowType() == RateLimitUsage.WindowType.MINUTE
                ? UserRateLimitTrackingEntity.WindowType.MINUTE
                : UserRateLimitTrackingEntity.WindowType.DAY;

        return new UserRateLimitTrackingEntity(
                domainModel.getId(),
                domainModel.getUserId(),
                domainModel.getWindowStart(),
                entityWindowType,
                domainModel.getRequestCount(),
                domainModel.getCreatedAt(),
                domainModel.getUpdatedAt()
        );
    }

    /**
     * Update an existing entity with values from domain model
     * @param entity The entity to update
     * @param domainModel The domain model with new values
     */
    public void updateEntity(UserRateLimitEntity entity, RateLimitConfig domainModel) {
        if (entity == null || domainModel == null) {
            return;
        }

        entity.setTranscriptsPerMinuteLimit(domainModel.getTranscriptsPerMinuteLimit());
        entity.setTranscriptsPerDayLimit(domainModel.getTranscriptsPerDayLimit());
        entity.setTotalTranscriptsLimit(domainModel.getTotalTranscriptsLimit());
        // updatedAt will be set automatically by @PreUpdate
    }

    /**
     * Update an existing tracking entity with values from domain model
     * @param entity The entity to update
     * @param domainModel The domain model with new values
     */
    public void updateEntity(UserRateLimitTrackingEntity entity, RateLimitUsage domainModel) {
        if (entity == null || domainModel == null) {
            return;
        }

        entity.setRequestCount(domainModel.getRequestCount());
        // updatedAt will be set automatically by @PreUpdate
    }

    /**
     * Convert entity WindowType to domain WindowType
     * @param entityWindowType The entity window type
     * @return The domain window type
     */
    public RateLimitUsage.WindowType toDomainWindowType(UserRateLimitTrackingEntity.WindowType entityWindowType) {
        if (entityWindowType == null) {
            return null;
        }
        return entityWindowType == UserRateLimitTrackingEntity.WindowType.MINUTE
                ? RateLimitUsage.WindowType.MINUTE
                : RateLimitUsage.WindowType.DAY;
    }

    /**
     * Convert domain WindowType to entity WindowType
     * @param domainWindowType The domain window type
     * @return The entity window type
     */
    public UserRateLimitTrackingEntity.WindowType toEntityWindowType(RateLimitUsage.WindowType domainWindowType) {
        if (domainWindowType == null) {
            return null;
        }
        return domainWindowType == RateLimitUsage.WindowType.MINUTE
                ? UserRateLimitTrackingEntity.WindowType.MINUTE
                : UserRateLimitTrackingEntity.WindowType.DAY;
    }
}