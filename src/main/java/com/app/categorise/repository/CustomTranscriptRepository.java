package com.app.categorise.repository;

import com.app.categorise.models.entity.Transcript;

import java.time.Instant;
import java.util.List;

public interface CustomTranscriptRepository {
    List<Transcript> filter(List<String> categories, String account, Instant from, Instant to);
}
