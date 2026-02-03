package com.app.categorise.application.mapper;

import com.app.categorise.data.entity.UserSubscriptionEntity;
import com.app.categorise.domain.model.UserSubscription;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between subscription domain models and entities
 */
@Component
public class SubscriptionMapper {
    
    public UserSubscription toDomainModel(UserSubscriptionEntity entity) {
        if (entity == null) {
            return null;
        }

        return new UserSubscription(
            entity.getId(),
            entity.getUserId(),
            mapToDomainType(entity.getSubscriptionType()),
            mapToDomainStatus(entity.getStatus()),
            entity.getGooglePlayPurchaseToken(),
            entity.getGooglePlayProductId(),
            entity.getGooglePlayOrderId(),
            entity.getSubscriptionStartDate(),
            entity.getSubscriptionEndDate(),
            entity.isAutoRenew(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
    
    public UserSubscriptionEntity toEntity(UserSubscription domain) {
        if (domain == null) {
            return null;
        }
        
        UserSubscriptionEntity entity = new UserSubscriptionEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setSubscriptionType(mapToEntityType(domain.getSubscriptionType()));
        entity.setStatus(mapToEntityStatus(domain.getStatus()));
        entity.setGooglePlayPurchaseToken(domain.getGooglePlayPurchaseToken());
        entity.setGooglePlayProductId(domain.getGooglePlayProductId());
        entity.setGooglePlayOrderId(domain.getGooglePlayOrderId());
        entity.setSubscriptionStartDate(domain.getSubscriptionStartDate());
        entity.setSubscriptionEndDate(domain.getSubscriptionEndDate());
        entity.setAutoRenew(domain.isAutoRenew());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }
    
    private UserSubscription.SubscriptionType mapToDomainType(UserSubscriptionEntity.SubscriptionType entityType) {
        return switch (entityType) {
            case FREE -> UserSubscription.SubscriptionType.FREE;
            case PREMIUM_MONTHLY -> UserSubscription.SubscriptionType.PREMIUM_MONTHLY;
            case PREMIUM_YEARLY -> UserSubscription.SubscriptionType.PREMIUM_YEARLY;
        };
    }

    private UserSubscriptionEntity.SubscriptionType mapToEntityType(UserSubscription.SubscriptionType domainType) {
        return switch (domainType) {
            case FREE -> UserSubscriptionEntity.SubscriptionType.FREE;
            case PREMIUM_MONTHLY -> UserSubscriptionEntity.SubscriptionType.PREMIUM_MONTHLY;
            case PREMIUM_YEARLY -> UserSubscriptionEntity.SubscriptionType.PREMIUM_YEARLY;
        };
    }
    
    private UserSubscription.SubscriptionStatus mapToDomainStatus(UserSubscriptionEntity.SubscriptionStatus entityStatus) {
        return switch (entityStatus) {
            case ACTIVE -> UserSubscription.SubscriptionStatus.ACTIVE;
            case CANCELLED -> UserSubscription.SubscriptionStatus.CANCELLED;
            case EXPIRED -> UserSubscription.SubscriptionStatus.EXPIRED;
            case PENDING -> UserSubscription.SubscriptionStatus.PENDING;
        };
    }

    private UserSubscriptionEntity.SubscriptionStatus mapToEntityStatus(UserSubscription.SubscriptionStatus domainStatus) {
        return switch (domainStatus) {
            case ACTIVE -> UserSubscriptionEntity.SubscriptionStatus.ACTIVE;
            case CANCELLED -> UserSubscriptionEntity.SubscriptionStatus.CANCELLED;
            case EXPIRED -> UserSubscriptionEntity.SubscriptionStatus.EXPIRED;
            case PENDING -> UserSubscriptionEntity.SubscriptionStatus.PENDING;
        };
    }
}