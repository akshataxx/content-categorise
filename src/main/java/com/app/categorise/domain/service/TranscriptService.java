package com.app.categorise.domain.service;

import com.app.categorise.application.mapper.TranscriptMapper;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.data.repository.TranscriptRepository;
import com.app.categorise.domain.model.Transcript;
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
 * Service for managing transcripts.
 * This service provides methods to find a transcript by ID and filter transcripts based on categories, account, and time range.
 * It interacts with the TranscriptRepository to perform database operations.
 */
@Service
public class TranscriptService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptService.class);

    private final TranscriptRepository transcriptRepository;
    private final TranscriptMapper transcriptMapper;

    public TranscriptService(TranscriptRepository transcriptRepository, TranscriptMapper transcriptMapper) {
        this.transcriptRepository = transcriptRepository;
        this.transcriptMapper = transcriptMapper;
    }

    public Optional<Transcript> findTranscript(UUID id) {
        if (id != null) {
            return transcriptRepository.findById(id).map(transcriptMapper::toDomain);
        }
        return Optional.empty();
    }

    public List<Transcript> allFilteredTranscripts(List<UUID> categories, String account, Instant from, Instant to) {
        return transcriptRepository.filter(categories, account, from, to)
            .stream()
            .map(transcriptMapper::toDomain)
            .toList();
    }

    public Transcript save(TranscriptEntity transcript) {
        System.out.println(transcript);
         TranscriptEntity entity = transcriptRepository.save(transcript);
         return transcriptMapper.toDomain(entity);
    }

    @Transactional
    public void deleteTranscripts(List<UUID> transcriptIds) {
        if (transcriptIds == null || transcriptIds.isEmpty()) {
            throw new IllegalArgumentException("Transcript IDs list cannot be null or empty");
        }

        logger.info("Attempting to delete {} transcripts", transcriptIds.size());

        try {
            // First, check which transcripts exist
            List<UUID> existingIds = transcriptRepository.findAllById(transcriptIds)
                    .stream()
                    .map(TranscriptEntity::getId)
                    .toList();

            // Find non-existent IDs
            List<UUID> nonExistentIds = transcriptIds.stream()
                    .filter(id -> !existingIds.contains(id))
                    .collect(Collectors.toList());

            if (!nonExistentIds.isEmpty()) {
                String message = String.format("The following transcript IDs were not found: %s", nonExistentIds);
                logger.warn(message);
                throw new TranscriptNotFoundException(message, nonExistentIds);
            }

            // Proceed with deletion if all transcripts exist
            transcriptRepository.deleteAllById(transcriptIds);
            logger.info("Successfully deleted {} transcripts", transcriptIds.size());

        } catch (TranscriptNotFoundException ex) {
            throw ex;
        }  catch (Exception ex) {
            String message = String.format("Unexpected error occurred while deleting transcripts: %s", ex.getMessage());
            logger.error(message, ex);
            throw new TranscriptDeletionException(message, transcriptIds, ex);
        }
    }
}
