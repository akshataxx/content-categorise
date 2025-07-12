package com.app.categorise.domain.service;

import com.app.categorise.application.mapper.TranscriptMapper;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.data.repository.TranscriptRepository;
import com.app.categorise.domain.model.Transcript;
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
}
