package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserTranscriptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserTranscriptRepository extends JpaRepository<UserTranscriptEntity, UUID> {
    
    /**
     * Find user transcript association by user ID and base transcript ID
     * Uses JOIN FETCH to avoid N+1 queries
     */
    @Query("SELECT ut FROM UserTranscriptEntity ut " +
           "JOIN FETCH ut.baseTranscript bt " +
           "WHERE ut.userId = :userId AND bt.id = :baseTranscriptId")
    Optional<UserTranscriptEntity> findByUserIdAndBaseTranscriptIdWithBaseTranscript(
            @Param("userId") UUID userId, 
            @Param("baseTranscriptId") UUID baseTranscriptId);
    
    /**
     * Find all user transcripts for a user with base transcript data loaded
     * Uses JOIN FETCH to avoid N+1 queries when accessing base transcript data
     */
    @Query("SELECT ut FROM UserTranscriptEntity ut " +
           "JOIN FETCH ut.baseTranscript bt " +
           "WHERE ut.userId = :userId " +
           "ORDER BY ut.createdAt DESC")
    List<UserTranscriptEntity> findByUserIdWithBaseTranscript(@Param("userId") UUID userId);
    
    /**
     * Find all user transcripts for a user with both base transcript and category data loaded
     * Uses multiple JOIN FETCH to load all related data in one query
     */
    @Query("SELECT ut FROM UserTranscriptEntity ut " +
           "JOIN FETCH ut.baseTranscript bt " +
           "LEFT JOIN FETCH ut.category c " +
           "WHERE ut.userId = :userId " +
           "ORDER BY ut.createdAt DESC")
    List<UserTranscriptEntity> findByUserIdWithBaseTranscriptAndCategory(@Param("userId") UUID userId);
    
    /**
     * Find user transcripts by category with base transcript data
     * Uses JOIN FETCH to avoid N+1 queries
     */
    @Query("SELECT ut FROM UserTranscriptEntity ut " +
           "JOIN FETCH ut.baseTranscript bt " +
           "LEFT JOIN FETCH ut.category c " +
           "WHERE ut.userId = :userId AND c.id = :categoryId " +
           "ORDER BY ut.createdAt DESC")
    List<UserTranscriptEntity> findByUserIdAndCategoryIdWithBaseTranscript(
            @Param("userId") UUID userId, 
            @Param("categoryId") UUID categoryId);
    
    /**
     * Check if user already has access to a specific base transcript
     * Simple existence check - no JOIN FETCH needed
     */
    @Query("SELECT COUNT(ut) > 0 FROM UserTranscriptEntity ut " +
           "WHERE ut.userId = :userId AND ut.baseTranscript.id = :baseTranscriptId")
    boolean existsByUserIdAndBaseTranscriptId(@Param("userId") UUID userId, @Param("baseTranscriptId") UUID baseTranscriptId);
    
    /**
     * Find user transcript by user and video URL
     * Uses JOIN to find transcript by video URL without loading full data
     */
    @Query("SELECT ut FROM UserTranscriptEntity ut " +
           "JOIN ut.baseTranscript bt " +
           "WHERE ut.userId = :userId AND bt.videoUrl = :videoUrl")
    Optional<UserTranscriptEntity> findByUserIdAndVideoUrl(
            @Param("userId") UUID userId, 
            @Param("videoUrl") String videoUrl);
    
    /**
     * Find user transcript by user and video URL with all data loaded
     * Uses JOIN FETCH for when you need the full data
     */
    @Query("SELECT ut FROM UserTranscriptEntity ut " +
           "JOIN FETCH ut.baseTranscript bt " +
           "LEFT JOIN FETCH ut.category c " +
           "WHERE ut.userId = :userId AND bt.videoUrl = :videoUrl")
    Optional<UserTranscriptEntity> findByUserIdAndVideoUrlWithFullData(
            @Param("userId") UUID userId, 
            @Param("videoUrl") String videoUrl);
}