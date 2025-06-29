package com.app.categorise.data.repository;

import com.app.categorise.data.entity.TranscriptEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CustomTranscriptRepository {
    List<TranscriptEntity> filter(List<UUID> categories, String account, Instant from, Instant to);
}
