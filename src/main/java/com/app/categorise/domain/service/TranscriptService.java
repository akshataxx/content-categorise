package com.app.categorise.domain.service;

import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.exception.TranscriptDeletionException;
import com.app.categorise.exception.TranscriptNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing user transcripts.
 * This service provides methods to find user transcripts by ID and filter transcripts based on categories, account, and time range.
 * It interacts with the UserTranscriptRepository and BaseTranscriptRepository to perform database operations.
 */
@Service
public class TranscriptService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptService.class);

    private final UserTranscriptRepository userTranscriptRepository;
    private final VideoMapper videoMapper;

    public TranscriptService(
        UserTranscriptRepository userTranscriptRepository,
        VideoMapper videoMapper
    ) {
        this.userTranscriptRepository = userTranscriptRepository;
        this.videoMapper = videoMapper;
    }

    public Optional<TranscriptDtoWithAliases> findTranscript(UUID userTranscriptId, UUID userId) {
        if (userTranscriptId != null) {
            Optional<UserTranscriptEntity> userTranscript = userTranscriptRepository.findByIdAndUserId(userTranscriptId, userId);
            if (userTranscript.isPresent()) {
                return Optional.of(videoMapper.buildResponse(
                    userTranscript.get().getBaseTranscript(),
                    userTranscript.get()
                ));
            }
        }
        return Optional.empty();
    }

    public List<TranscriptDtoWithAliases> allFilteredTranscripts(UUID userId, List<UUID> categories, String account, Instant from, Instant to) {
        // Use the custom repository method for efficient database filtering
        List<UserTranscriptEntity> userTranscripts = userTranscriptRepository.filterByUser(userId, categories, account, from, to);
        
        return userTranscripts.stream()
            .filter(ut -> isValidTranscript(ut.getBaseTranscript()))
            .map(ut -> videoMapper.buildResponse(ut.getBaseTranscript(), ut))
            .toList();
    }

    private boolean isValidTranscript(BaseTranscriptEntity base) {
        if (base == null) return false;
        if (base.getTranscript() == null || base.getTranscript().isBlank()) {
            logger.warn("Excluding transcript id={} with blank transcript text", base.getId());
            return false;
        }
        if (base.getTitle() == null || base.getTitle().isBlank()) {
            logger.warn("Excluding transcript id={} with missing title", base.getId());
            return false;
        }
        if (base.getDuration() == null || base.getDuration() <= 0) {
            logger.warn("Excluding transcript id={} with invalid duration={}", base.getId(), base.getDuration());
            return false;
        }
        if (base.getUploadedAt() == null || base.getUploadedAt().getEpochSecond() == 0) {
            logger.warn("Excluding transcript id={} with invalid uploadedAt={}", base.getId(), base.getUploadedAt());
            return false;
        }
        return true;
    }

    public TranscriptDtoWithAliases save(UserTranscriptEntity userTranscript) {
        System.out.println(userTranscript);
        UserTranscriptEntity entity = userTranscriptRepository.save(userTranscript);
        return videoMapper.buildResponse(entity.getBaseTranscript(), entity);
    }

    /**
     * Update notes for a user transcript.
     * Validates that the transcript belongs to the requesting user.
     * @param userId The authenticated user's ID
     * @param userTranscriptId The user transcript ID to update
     * @param notes The new notes content (null to clear)
     */
    @Transactional
    public void updateNotes(UUID userId, UUID userTranscriptId, String notes) {
        UserTranscriptEntity entity = userTranscriptRepository
            .findByIdAndUserId(userTranscriptId, userId)
            .orElseThrow(() -> new TranscriptNotFoundException(
                "Transcript not found: " + userTranscriptId,
                List.of(userTranscriptId)
            ));

        entity.setNotes(notes);
        userTranscriptRepository.save(entity);
    }

    @Transactional
    public void deleteTranscripts(UUID userId, List<UUID> userTranscriptIds) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (userTranscriptIds == null || userTranscriptIds.isEmpty()) {
            throw new IllegalArgumentException("User transcript IDs list cannot be null or empty");
        }

        logger.info("Attempting to delete {} user transcripts for user {}", userTranscriptIds.size(), userId);

        try {
            List<UserTranscriptEntity> existingEntities = userTranscriptRepository.findAllByIdInAndUserId(userTranscriptIds, userId);
            Set<UUID> existingIds = existingEntities.stream()
                .map(UserTranscriptEntity::getId)
                .collect(Collectors.toSet());

            List<UUID> nonExistentIds = userTranscriptIds.stream()
                .filter(id -> !existingIds.contains(id))
                .toList();

            if (!nonExistentIds.isEmpty()) {
                String message = String.format("The following user transcript IDs were not found for user %s: %s", userId, nonExistentIds);
                logger.warn(message);
                throw new TranscriptNotFoundException(message, nonExistentIds);
            }

            userTranscriptRepository.deleteAll(existingEntities);
            logger.info("Successfully deleted {} user transcripts for user {}", userTranscriptIds.size(), userId);

        } catch (TranscriptNotFoundException ex) {
            throw ex;
        }  catch (Exception ex) {
            String message = String.format("Unexpected error occurred while deleting user transcripts for user %s: %s", userId, ex.getMessage());
            logger.error(message, ex);
            throw new TranscriptDeletionException(message, userTranscriptIds, ex);
        }
    }
}
