package com.app.categorise.data.repository;

import com.app.categorise.data.entity.TranscriptEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranscriptRepository extends MongoRepository<TranscriptEntity, String>, CustomTranscriptRepository {}
