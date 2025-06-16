package com.app.categorise.service;

import com.app.categorise.models.entity.Transcript;
import com.app.categorise.repository.TranscriptRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class TranscriptService {

    private final TranscriptRepository transcriptRepository;

    public TranscriptService(TranscriptRepository transcriptRepository) {
        this.transcriptRepository = transcriptRepository;
    }

    public Optional<Transcript> findTranscript(String id) {
        if (id != null && !id.isEmpty()) {
            return transcriptRepository.findById(id);

        }
        return Optional.empty();
    }

    public List<Transcript> filterTranscripts(List<String> categories, String account, Instant from, Instant to) {
        return transcriptRepository.filter(categories, account, from, to);
    }
}
