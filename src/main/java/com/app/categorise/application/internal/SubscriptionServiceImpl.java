package com.app.categorise.application.internal;

import com.app.categorise.application.mapper.SubscriptionMapper;
import com.app.categorise.data.entity.UserSubscriptionEntity;
import com.app.categorise.data.repository.UserSubscriptionRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.domain.model.SubscriptionSource;
import com.app.categorise.domain.model.UserSubscription;
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
    public Optional<UserSubscription> getUserSubscription(UUID userId) {
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

        // Must be a premium subscription type
        boolean isPremium = sub.getSubscriptionType() == UserSubscriptionEntity.SubscriptionType.PREMIUM_MONTHLY ||
                           sub.getSubscriptionType() == UserSubscriptionEntity.SubscriptionType.PREMIUM_YEARLY;

        if (!isPremium) {
            return false;
        }

        // Expired subscriptions have no access
        if (sub.getStatus() == UserSubscriptionEntity.SubscriptionStatus.EXPIRED) {
            return false;
        }

        // If no end date, only ACTIVE subscriptions have access (auto-renewing)
        if (sub.getSubscriptionEndDate() == null) {
            return sub.getStatus() == UserSubscriptionEntity.SubscriptionStatus.ACTIVE;
        }

        // If end date exists, user has access until it expires (even if CANCELLED)
        // User paid for the period, so they get access until the end date
        return sub.getSubscriptionEndDate().isAfter(Instant.now());
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
    public UserSubscription upgradeToPremiumWithGooglePlay(UUID userId, String purchaseToken,
                                                           String productId, String orderId,
                                                           UserSubscription.SubscriptionType type) {
        logger.info("Upgrading user {} to premium via Google Play: {}", userId, type);

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

        // Update to premium with Google Play details
        entity.setSubscriptionType(mapToEntityType(type));
        entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.ACTIVE);
        entity.setGooglePlayPurchaseToken(purchaseToken);
        entity.setGooglePlayProductId(productId);
        entity.setGooglePlayOrderId(orderId);
        entity.setSubscriptionSource(SubscriptionSource.GOOGLE_PLAY);
        entity.setSubscriptionStartDate(Instant.now());

        // Set end date based on subscription type
        // Note: For Google Play, actual expiry is managed by Google and synced via RTDN
        if (type == UserSubscription.SubscriptionType.PREMIUM_MONTHLY) {
            entity.setSubscriptionEndDate(Instant.now().plus(30, ChronoUnit.DAYS));
        } else if (type == UserSubscription.SubscriptionType.PREMIUM_YEARLY) {
            entity.setSubscriptionEndDate(Instant.now().plus(365, ChronoUnit.DAYS));
        }

        entity.setAutoRenew(true);

        UserSubscriptionEntity saved = subscriptionRepository.save(entity);
        logger.info("Successfully upgraded user {} to premium via Google Play", userId);

        return mapper.toDomainModel(saved);
    }
    
    @Override
    public void upgradeToPremiumWithAppStore(UUID userId, String originalTransactionId,
                                             String transactionId, String productId,
                                             Instant expirationDate) {
        logger.info("Upgrading user {} to premium via App Store", userId);

        // Find existing subscription or create new one
        UserSubscriptionEntity entity = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserSubscriptionEntity newSub = new UserSubscriptionEntity(
                            userId,
                            UserSubscriptionEntity.SubscriptionType.FREE,
                            UserSubscriptionEntity.SubscriptionStatus.ACTIVE
                    );
                    return newSub;
                });

        // Log platform switch if applicable
        if (entity.getSubscriptionSource() != null
                && entity.getSubscriptionSource() != SubscriptionSource.APP_STORE) {
            logger.info("User {} switching subscription platform from {} to APP_STORE",
                    userId, entity.getSubscriptionSource());
        }

        // Determine subscription type from product ID
        UserSubscriptionEntity.SubscriptionType type = productId.contains("yearly")
                ? UserSubscriptionEntity.SubscriptionType.PREMIUM_YEARLY
                : UserSubscriptionEntity.SubscriptionType.PREMIUM_MONTHLY;

        // Update subscription details
        entity.setSubscriptionType(type);
        entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.ACTIVE);
        entity.setAppleOriginalTransactionId(originalTransactionId);
        entity.setAppleTransactionId(transactionId);
        entity.setAppleProductId(productId);
        entity.setSubscriptionSource(SubscriptionSource.APP_STORE);
        entity.setSubscriptionStartDate(Instant.now());
        entity.setSubscriptionEndDate(expirationDate);
        entity.setAutoRenew(true);

        subscriptionRepository.save(entity);

        logger.info("User {} upgraded to {} via App Store, expires {}",
                userId, type, expirationDate);
    }

    @Override
    public void cancelSubscription(UUID userId) {
        logger.info("Cancelling subscription for user: {}", userId);

        Optional<UserSubscriptionEntity> subscriptionOpt = subscriptionRepository.findByUserId(userId);
        if (subscriptionOpt.isPresent()) {
            UserSubscriptionEntity entity = subscriptionOpt.get();

            // For Google Play, cancellation is handled through the Play Store
            // The user must cancel via Google Play subscription management
            // We receive RTDN notifications when they do
            // Here we just mark it as cancelled in our DB
            entity.setStatus(UserSubscriptionEntity.SubscriptionStatus.CANCELLED);
            entity.setAutoRenew(false);
            subscriptionRepository.save(entity);

            logger.info("Subscription marked as cancelled for user: {}. " +
                    "User should cancel via Google Play Store to stop billing.", userId);
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
    
    private UserSubscriptionEntity.SubscriptionType mapToEntityType(UserSubscription.SubscriptionType domainType) {
        return switch (domainType) {
            case FREE -> UserSubscriptionEntity.SubscriptionType.FREE;
            case PREMIUM_MONTHLY -> UserSubscriptionEntity.SubscriptionType.PREMIUM_MONTHLY;
            case PREMIUM_YEARLY -> UserSubscriptionEntity.SubscriptionType.PREMIUM_YEARLY;
        };
    }
}