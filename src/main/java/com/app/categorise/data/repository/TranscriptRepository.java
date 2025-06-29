package com.app.categorise.data.repository;

import com.app.categorise.data.entity.TranscriptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TranscriptRepository extends JpaRepository<TranscriptEntity, UUID>, CustomTranscriptRepository {
}
