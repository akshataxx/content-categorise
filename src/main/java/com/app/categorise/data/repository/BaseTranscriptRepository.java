package com.app.categorise.data.repository;

import com.app.categorise.data.entity.BaseTranscriptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BaseTranscriptRepository extends JpaRepository<BaseTranscriptEntity, UUID> {

    /**
     * Find base transcript by video URL to check for existing transcripts.
     * This is the Tier-1 dedup path: identical URL strings reuse the same row.
     */
    Optional<BaseTranscriptEntity> findByVideoUrl(String videoUrl);

    /**
     * Check if a transcript exists for a given video URL
     */
    boolean existsByVideoUrl(String videoUrl);

    /**
     * Tier-2 dedup: find an existing base transcript by canonical
     * {@code (platform, platform_video_id)}. This catches the case where the
     * same underlying video is shared via different URL forms
     * (e.g. {@code youtu.be/X} vs {@code youtube.com/watch?v=X} vs
     * {@code youtube.com/shorts/X}).
     */
    Optional<BaseTranscriptEntity> findByPlatformAndPlatformVideoId(String platform, String platformVideoId);
}