package com.app.categorise.data.repository;

import com.app.categorise.data.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DeviceRepository - Data access for devices table.
 * Used by NotificationService to find active devices for push notifications
 * and by DeviceController for registration/unregistration.
 */
@Repository
public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    /**
     * Find all active devices for a user. Primary method for notification dispatch.
     */
    List<DeviceEntity> findByUserIdAndActiveTrue(UUID userId);

    /**
     * Find device by FCM token (e.g. for token refresh / upsert).
     */
    Optional<DeviceEntity> findByFcmToken(String fcmToken);

    /**
     * Find device by user and device ID (e.g. for unregister by device).
     */
    Optional<DeviceEntity> findByUserIdAndDeviceId(UUID userId, String deviceId);

    @Modifying
    @Transactional
    void deleteByFcmToken(String fcmToken);

    @Modifying
    @Transactional
    void deleteByDeviceId(String deviceId);
}
