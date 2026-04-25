package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserTranscriptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserTranscriptRepository extends JpaRepository<UserTranscriptEntity, UUID>, CustomUserTranscriptRepository {
    
    /**
     * Find user transcript association by user ID and base transcript ID
     */
    Optional<UserTranscriptEntity> findByUserIdAndBaseTranscript_Id(UUID userId, UUID baseTranscriptId);
    
    /**
     * Find all user transcripts for a user ordered by creation date
     */
    @Query("SELECT ut FROM UserTranscriptEntity ut " +
           "WHERE ut.userId = :userId " +
           "ORDER BY ut.createdAt DESC")
    List<UserTranscriptEntity> findByUserId(@Param("userId") UUID userId);
    
    /**
     * Find user transcripts by category
     */
    @Query("SELECT ut FROM UserTranscriptEntity ut " +
           "WHERE ut.userId = :userId AND ut.category.id = :categoryId " +
           "ORDER BY ut.createdAt DESC")
    List<UserTranscriptEntity> findByUserIdAndCategoryId(
            @Param("userId") UUID userId, 
            @Param("categoryId") UUID categoryId);
    
    /**
     * Check if user already has access to a specific base transcript
     */
    boolean existsByUserIdAndBaseTranscript_Id(UUID userId, UUID baseTranscriptId);
    
    /**
     * Find user transcript by user and video URL
     */
    @Query("SELECT ut FROM UserTranscriptEntity ut " +
           "JOIN ut.baseTranscript bt " +
           "WHERE ut.userId = :userId AND bt.videoUrl = :videoUrl")
    Optional<UserTranscriptEntity> findByUserIdAndVideoUrl(
            @Param("userId") UUID userId, 
            @Param("videoUrl") String videoUrl);

    /**
     * Count total number of transcripts for a specific user
     * Used for rate limiting total transcript count
     * @param userId The user ID
     * @return Total number of transcripts for the user
     */
    long countByUserId(UUID userId);

    /**
     * Find user transcript by ID and user ID for ownership validation
     * @param id The user transcript ID
     * @param userId The user ID
     * @return The user transcript if found and owned by the user
     */
    Optional<UserTranscriptEntity> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Find user transcripts by IDs scoped to a single owner.
     * Used by bulk delete to prevent cross-user deletion.
     */
    List<UserTranscriptEntity> findAllByIdInAndUserId(Collection<UUID> ids, UUID userId);
}
