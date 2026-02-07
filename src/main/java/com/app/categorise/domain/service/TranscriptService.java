package com.app.categorise.domain.service;

import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.BaseTranscriptRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.domain.service.CategoryAliasService;
import com.app.categorise.exception.TranscriptDeletionException;
import com.app.categorise.exception.TranscriptNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private final BaseTranscriptRepository baseTranscriptRepository;
    private final VideoMapper videoMapper;

    public TranscriptService(
        UserTranscriptRepository userTranscriptRepository,
        BaseTranscriptRepository baseTranscriptRepository,
        VideoMapper videoMapper
    ) {
        this.userTranscriptRepository = userTranscriptRepository;
        this.baseTranscriptRepository = baseTranscriptRepository;
        this.videoMapper = videoMapper;
    }

    public Optional<TranscriptDtoWithAliases> findTranscript(UUID userTranscriptId, UUID userId) {
        if (userTranscriptId != null) {
            Optional<UserTranscriptEntity> userTranscript = userTranscriptRepository.findByIdWithFullData(userTranscriptId);
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
            .map(ut -> videoMapper.buildResponse(ut.getBaseTranscript(), ut))
            .toList();
    }

    public TranscriptDtoWithAliases save(UserTranscriptEntity userTranscript) {
        System.out.println(userTranscript);
        UserTranscriptEntity entity = userTranscriptRepository.save(userTranscript);
        return videoMapper.buildResponse(entity.getBaseTranscript(), entity);
    }

    @Transactional
    public void deleteTranscripts(List<UUID> userTranscriptIds) {
        if (userTranscriptIds == null || userTranscriptIds.isEmpty()) {
            throw new IllegalArgumentException("User transcript IDs list cannot be null or empty");
        }

        logger.info("Attempting to delete {} user transcripts", userTranscriptIds.size());

        try {
            // First, check which user transcripts exist
            List<UUID> existingIds = userTranscriptRepository.findAllById(userTranscriptIds)
                    .stream()
                    .map(UserTranscriptEntity::getId)
                    .toList();

            // Find non-existent IDs
            List<UUID> nonExistentIds = userTranscriptIds.stream()
                    .filter(id -> !existingIds.contains(id))
                    .collect(Collectors.toList());

            if (!nonExistentIds.isEmpty()) {
                String message = String.format("The following user transcript IDs were not found: %s", nonExistentIds);
                logger.warn(message);
                throw new TranscriptNotFoundException(message, nonExistentIds);
            }

            // Proceed with deletion if all user transcripts exist
            userTranscriptRepository.deleteAllById(userTranscriptIds);
            logger.info("Successfully deleted {} user transcripts", userTranscriptIds.size());

        } catch (TranscriptNotFoundException ex) {
            throw ex;
        }  catch (Exception ex) {
            String message = String.format("Unexpected error occurred while deleting user transcripts: %s", ex.getMessage());
            logger.error(message, ex);
            throw new TranscriptDeletionException(message, userTranscriptIds, ex);
        }
    }
}
