package com.app.categorise.domain.service;

import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.data.repository.TranscriptRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing transcripts.
 * This service provides methods to find a transcript by ID and filter transcripts based on categories, account, and time range.
 * It interacts with the TranscriptRepository to perform database operations.
 */
@Service
public class TranscriptService {

    private final TranscriptRepository transcriptRepository;

    public TranscriptService(TranscriptRepository transcriptRepository) {
        this.transcriptRepository = transcriptRepository;
    }

    public Optional<TranscriptEntity> findTranscript(UUID id) {
        if (id != null) {
            return transcriptRepository.findById(id);
        }
        return Optional.empty();
    }

    public List<TranscriptEntity> allFilteredTranscripts(List<UUID> categories, String account, Instant from, Instant to) {
        return transcriptRepository.filter(categories, account, from, to);
    }

    public TranscriptEntity save(TranscriptEntity transcript) {
        System.out.println(transcript);
        return transcriptRepository.save(transcript);
    }
}
