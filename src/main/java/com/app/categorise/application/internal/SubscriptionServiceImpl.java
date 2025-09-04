package com.app.categorise.application.internal;

import com.app.categorise.application.mapper.SubscriptionMapper;
import com.app.categorise.data.entity.UserSubscriptionEntity;
import com.app.categorise.data.repository.UserSubscriptionRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.domain.model.Subscription;
import com.app.categorise.domain.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of subscription service
 */
@Service
@Transactional
public class SubscriptionServiceImpl implements SubscriptionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionServiceImpl.class);
    private static final int FREE_TIER_LIMIT = 3;
    
    private final UserSubscriptionRepository subscriptionRepository;
    private final UserTranscriptRepository transcriptRepository;
    private final SubscriptionMapper mapper;
    
    public SubscriptionServiceImpl(UserSubscriptionRepository subscriptionRepository,
                                  UserTranscriptRepository transcriptRepository,
                                  SubscriptionMapper mapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.transcriptRepository = transcriptRepository;
        this.mapper = mapper;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> getUserSubscription(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(mapper::toDomainModel);
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean hasActivePremiumSubscription(UUID userId) {
        Optional<UserSubscriptionEntity> subscription = subscriptionRepository.findByUserId(userId);
        
        if (subscription.isEmpty()) {
            return false;
        }
        
        UserSubscriptionEntity sub = subscription.get();
        boolean isPremium = sub.getSubscriptionType() == UserSubscriptionEntity.SubscriptionType.PREMIUM_MONTHLY ||
                           sub.getSubscriptionType() == UserSubscriptionEntity.SubscriptionType.PREMIUM_YEARLY;
        boolean isActive = sub.getStatus() == UserSubscriptionEntity.SubscriptionStatus.ACTIVE;
        boolean notExpired = sub.getSubscriptionEndDate() == null || 
                            sub.getSubscriptionEndDate().isAfter(Instant.now());
        
        return isPremium && isActive && notExpired;
    }
    
    @Override
    public void initializeFreeSubscription(UUID userId) {
        if (!subscriptionRepository.existsByUserId(userId)) {
            logger.info("Initializing free subscription for user: {}", userId);
            
            UserSubscriptionEntity entity = new UserSubscriptionEntity(
                userId,
                UserSubscriptionEntity.SubscriptionType.FREE,
                UserSubscriptionEntity.SubscriptionStatus.ACTIVE
            );
            
            subscriptionRepository.save(entity);
            logger.info("Free subscription initialized for user: {}", userId);
        }
    }
    
    @Override
    public Subscription upgradeToPremium(UUID userId, String googlePlayPurchaseToken, 
                                       String googlePlayProductId, Subscription.SubscriptionType type) {
        logger.info("Upgrading user {} to premium subscription type: {}", userId, type);
        
        // Find existing subscription or create new one
        Optional<UserSubscriptionEntity> existingOpt = subscriptionRepository.findByUserId(userId);
        UserSubscriptionEntity entity;
        
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
        } else {
            entity = new UserSubscriptionEntity(userId, 
                UserSubscriptionEntity.SubscriptionType.FREE, 
                UserSubscriptionEntity.SubscriptionStatus.ACTIVE);
        }
        
        // Update to premium
        entity.setSubscriptionType(mapToEntityType(type));
        entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.ACTIVE);
        entity.setGooglePlayPurchaseToken(googlePlayPurchaseToken);
        entity.setGooglePlayProductId(googlePlayProductId);
        entity.setSubscriptionStartDate(Instant.now());
        
        // Set end date based on subscription type
        if (type == Subscription.SubscriptionType.PREMIUM_MONTHLY) {
            entity.setSubscriptionEndDate(Instant.now().plus(30, ChronoUnit.DAYS));
        } else if (type == Subscription.SubscriptionType.PREMIUM_YEARLY) {
            entity.setSubscriptionEndDate(Instant.now().plus(365, ChronoUnit.DAYS));
        }
        
        entity.setAutoRenew(true);
        
        UserSubscriptionEntity saved = subscriptionRepository.save(entity);
        logger.info("Successfully upgraded user {} to premium", userId);
        
        return mapper.toDomainModel(saved);
    }
    
    @Override
    public boolean verifyGooglePlayPurchase(String purchaseToken, String productId) {
        // TODO: Implement Google Play Developer API verification
        // For MVP, we'll trust the client verification
        logger.info("Verifying Google Play purchase: token={}, productId={}", purchaseToken, productId);
        return true; // For MVP - implement proper verification later
    }
    
    @Override
    public void cancelSubscription(UUID userId) {
        logger.info("Cancelling subscription for user: {}", userId);
        
        Optional<UserSubscriptionEntity> subscriptionOpt = subscriptionRepository.findByUserId(userId);
        if (subscriptionOpt.isPresent()) {
            UserSubscriptionEntity entity = subscriptionOpt.get();
            entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.CANCELLED);
            entity.setAutoRenew(false);
            subscriptionRepository.save(entity);
            
            logger.info("Subscription cancelled for user: {}", userId);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public int getRemainingFreeTranscriptions(UUID userId) {
        // Check if user has premium subscription
        if (hasActivePremiumSubscription(userId)) {
            return Integer.MAX_VALUE; // Unlimited for premium users
        }
        
        // For free users, calculate remaining from FREE_TIER_LIMIT
        long usedTranscriptions = transcriptRepository.countByUserId(userId);
        int remaining = FREE_TIER_LIMIT - (int) usedTranscriptions;
        return Math.max(0, remaining);
    }
    
    private UserSubscriptionEntity.SubscriptionType mapToEntityType(Subscription.SubscriptionType domainType) {
        return switch (domainType) {
            case FREE -> UserSubscriptionEntity.SubscriptionType.FREE;
            case PREMIUM_MONTHLY -> UserSubscriptionEntity.SubscriptionType.PREMIUM_MONTHLY;
            case PREMIUM_YEARLY -> UserSubscriptionEntity.SubscriptionType.PREMIUM_YEARLY;
        };
    }
}