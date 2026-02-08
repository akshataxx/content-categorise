package com.app.categorise.data.repository;

import com.app.categorise.data.entity.ProcessedNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for managing processed webhook notification records.
 * Used for idempotent webhook handling.
 */
@Repository
public interface ProcessedNotificationRepository extends JpaRepository<ProcessedNotificationEntity, UUID> {

    /**
     * Check if a notification has already been processed.
     *
     * @param notificationId The unique notification identifier from the webhook provider
     * @return true if the notification was already processed
     */
    boolean existsByNotificationId(String notificationId);
}
