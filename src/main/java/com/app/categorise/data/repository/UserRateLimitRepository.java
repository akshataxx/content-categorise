package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserRateLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * UserRateLimitRepository - JPA repository for UserRateLimitEntity
 * Provides data access methods for user rate limit configurations
 */
@Repository
public interface UserRateLimitRepository extends JpaRepository<UserRateLimitEntity, UUID> {

    /**
     * Find rate limit configuration by user ID
     * @param userId The user ID to search for
     * @return Optional containing the rate limit configuration if found
     */
    Optional<UserRateLimitEntity> findByUserId(UUID userId);

    /**
     * Check if rate limit configuration exists for a user
     * @param userId The user ID to check
     * @return true if configuration exists, false otherwise
     */
    boolean existsByUserId(UUID userId);

    /**
     * Delete rate limit configuration by user ID
     * @param userId The user ID whose configuration should be deleted
     */
    void deleteByUserId(UUID userId);

}