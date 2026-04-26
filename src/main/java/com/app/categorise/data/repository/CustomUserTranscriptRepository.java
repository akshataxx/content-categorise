package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserTranscriptEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CustomUserTranscriptRepository {
    List<UserTranscriptEntity> filterByUser(UUID userId, List<UUID> categories, String account, Instant from, Instant to);
    List<UserTranscriptEntity> filterByUser(UUID userId, List<UUID> categories, List<UUID> subcategories, String account, Instant from, Instant to);
}
