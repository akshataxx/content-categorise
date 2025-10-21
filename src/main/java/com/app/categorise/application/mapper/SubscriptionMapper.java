package com.app.categorise.application.mapper;

import com.app.categorise.data.entity.UserSubscriptionEntity;
import com.app.categorise.domain.model.Subscription;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between subscription domain models and entities
 */
@Component
public class SubscriptionMapper {
    
    public Subscription toDomainModel(UserSubscriptionEntity entity) {
        if (entity == null) {
            return null;
        }
        
        return new Subscription(
            entity.getId(),
            entity.getUserId(),
            mapToDomainType(entity.getSubscriptionType()),
            mapToDomainStatus(entity.getStatus()),
            entity.getStripeCustomerId(),
            entity.getStripeSubscriptionId(),
            entity.getStripePriceId(),
            entity.getSubscriptionStartDate(),
            entity.getSubscriptionEndDate(),
            entity.isAutoRenew(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
    
    public UserSubscriptionEntity toEntity(Subscription domain) {
        if (domain == null) {
            return null;
        }
        
        UserSubscriptionEntity entity = new UserSubscriptionEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setSubscriptionType(mapToEntityType(domain.getSubscriptionType()));
        entity.setStatus(mapToEntityStatus(domain.getStatus()));
        entity.setStripeCustomerId(domain.getStripeCustomerId());
        entity.setStripeSubscriptionId(domain.getStripeSubscriptionId());
        entity.setStripePriceId(domain.getStripePriceId());
        entity.setSubscriptionStartDate(domain.getSubscriptionStartDate());
        entity.setSubscriptionEndDate(domain.getSubscriptionEndDate());
        entity.setAutoRenew(domain.isAutoRenew());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }
    
    private Subscription.SubscriptionType mapToDomainType(UserSubscriptionEntity.SubscriptionType entityType) {
        return switch (entityType) {
            case FREE -> Subscription.SubscriptionType.FREE;
            case PREMIUM_MONTHLY -> Subscription.SubscriptionType.PREMIUM_MONTHLY;
            case PREMIUM_YEARLY -> Subscription.SubscriptionType.PREMIUM_YEARLY;
        };
    }
    
    private UserSubscriptionEntity.SubscriptionType mapToEntityType(Subscription.SubscriptionType domainType) {
        return switch (domainType) {
            case FREE -> UserSubscriptionEntity.SubscriptionType.FREE;
            case PREMIUM_MONTHLY -> UserSubscriptionEntity.SubscriptionType.PREMIUM_MONTHLY;
            case PREMIUM_YEARLY -> UserSubscriptionEntity.SubscriptionType.PREMIUM_YEARLY;
        };
    }
    
    private Subscription.SubscriptionStatus mapToDomainStatus(UserSubscriptionEntity.SubscriptionStatus entityStatus) {
        return switch (entityStatus) {
            case ACTIVE -> Subscription.SubscriptionStatus.ACTIVE;
            case CANCELLED -> Subscription.SubscriptionStatus.CANCELLED;
            case EXPIRED -> Subscription.SubscriptionStatus.EXPIRED;
            case PENDING -> Subscription.SubscriptionStatus.PENDING;
        };
    }
    
    private UserSubscriptionEntity.SubscriptionStatus mapToEntityStatus(Subscription.SubscriptionStatus domainStatus) {
        return switch (domainStatus) {
            case ACTIVE -> UserSubscriptionEntity.SubscriptionStatus.ACTIVE;
            case CANCELLED -> UserSubscriptionEntity.SubscriptionStatus.CANCELLED;
            case EXPIRED -> UserSubscriptionEntity.SubscriptionStatus.EXPIRED;
            case PENDING -> UserSubscriptionEntity.SubscriptionStatus.PENDING;
        };
    }
}