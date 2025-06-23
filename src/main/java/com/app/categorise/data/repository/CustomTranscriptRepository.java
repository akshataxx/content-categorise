package com.app.categorise.data.repository;

import com.app.categorise.data.entity.TranscriptEntity;

import java.time.Instant;
import java.util.List;

public interface CustomTranscriptRepository {
    List<TranscriptEntity> filter(List<String> categories, String account, Instant from, Instant to);
}
