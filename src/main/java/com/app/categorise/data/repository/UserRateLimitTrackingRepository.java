package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserRateLimitTrackingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UserRateLimitTrackingRepository - JPA repository for UserRateLimitTrackingEntity
 * Provides data access methods for tracking rate limit usage in time windows
 */
@Repository
public interface UserRateLimitTrackingRepository extends JpaRepository<UserRateLimitTrackingEntity, UUID> {

    /**
     * Find tracking record for a specific user, window start, and window type
     * @param userId The user ID
     * @param windowStart The start of the time window
     * @param windowType The type of window (MINUTE or DAY)
     * @return Optional containing the tracking record if found
     */
    Optional<UserRateLimitTrackingEntity> findByUserIdAndWindowStartAndWindowType(
            UUID userId, Instant windowStart, UserRateLimitTrackingEntity.WindowType windowType);

    /**
     * Find all tracking records for a user and window type
     * @param userId The user ID
     * @param windowType The type of window (MINUTE or DAY)
     * @return List of tracking records
     */
    List<UserRateLimitTrackingEntity> findByUserIdAndWindowType(
            UUID userId, UserRateLimitTrackingEntity.WindowType windowType);

    /**
     * Find tracking records for a user and window type within a time range
     * @param userId The user ID
     * @param windowType The type of window (MINUTE or DAY)
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return List of tracking records
     */
    @Query("SELECT urlt FROM UserRateLimitTrackingEntity urlt WHERE urlt.userId = :userId " +
           "AND urlt.windowType = :windowType " +
           "AND urlt.windowStart >= :startTime AND urlt.windowStart <= :endTime")
    List<UserRateLimitTrackingEntity> findByUserIdAndWindowTypeAndTimeRange(
            @Param("userId") UUID userId,
            @Param("windowType") UserRateLimitTrackingEntity.WindowType windowType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * Increment request count for an existing window record
     * @param userId The user ID
     * @param windowStart The start of the time window
     * @param windowType The type of window (MINUTE or DAY)
     * @return Number of rows affected (1 if record exists, 0 if not)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserRateLimitTrackingEntity urlt SET urlt.requestCount = urlt.requestCount + 1, " +
           "urlt.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE urlt.userId = :userId AND urlt.windowStart = :windowStart AND urlt.windowType = :windowType")
    int incrementRequestCount(@Param("userId") UUID userId,
                             @Param("windowStart") Instant windowStart,
                             @Param("windowType") UserRateLimitTrackingEntity.WindowType windowType);

    /**
     * Delete old tracking records for cleanup
     * @param windowType The type of window (MINUTE or DAY)
     * @param cutoffTime Records older than this time will be deleted
     * @return Number of records deleted
     */
    @Modifying
    @Query("DELETE FROM UserRateLimitTrackingEntity urlt WHERE urlt.windowType = :windowType " +
           "AND urlt.windowStart < :cutoffTime")
    int deleteOldTrackingRecords(@Param("windowType") UserRateLimitTrackingEntity.WindowType windowType,
                                @Param("cutoffTime") Instant cutoffTime);

    /**
     * Delete all tracking records for a specific user
     * @param userId The user ID
     */
    void deleteByUserId(UUID userId);

    /**
     * Count tracking records for cleanup monitoring
     * @param windowType The type of window (MINUTE or DAY)
     * @return Total number of records for the window type
     */
    @Query("SELECT COUNT(urlt) FROM UserRateLimitTrackingEntity urlt WHERE urlt.windowType = :windowType")
    long countByWindowType(@Param("windowType") UserRateLimitTrackingEntity.WindowType windowType);
}