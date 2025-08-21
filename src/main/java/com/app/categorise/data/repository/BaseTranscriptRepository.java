package com.app.categorise.data.repository;

import com.app.categorise.data.entity.BaseTranscriptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BaseTranscriptRepository extends JpaRepository<BaseTranscriptEntity, UUID> {
    
    /**
     * Find base transcript by video URL to check for existing transcripts
     */
    Optional<BaseTranscriptEntity> findByVideoUrl(String videoUrl);
    
    /**
     * Check if a transcript exists for a given video URL
     */
    boolean existsByVideoUrl(String videoUrl);
}