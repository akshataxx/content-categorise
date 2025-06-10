package com.app.categorise.repository;

import com.app.categorise.entity.TranscriptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptRepository extends JpaRepository<TranscriptEntity, Long> {

    List<TranscriptEntity> findByCategory(String category);
}
