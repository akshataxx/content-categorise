package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing user subscriptions
 */
@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscriptionEntity, UUID> {
    
    /**
     * Find subscription by user ID
     */
    Optional<UserSubscriptionEntity> findByUserId(UUID userId);
    
    /**
     * Check if user exists in subscriptions table
     */
    boolean existsByUserId(UUID userId);

    /**
     * Count active premium subscriptions
     */
    @Query("SELECT COUNT(s) FROM UserSubscriptionEntity s WHERE s.status = 'ACTIVE' AND s.subscriptionType IN ('PREMIUM_MONTHLY', 'PREMIUM_YEARLY')")
    long countActivePremiumSubscriptions();

    /**
     * Find subscription by Google Play purchase token
     */
    Optional<UserSubscriptionEntity> findByGooglePlayPurchaseToken(String purchaseToken);
}