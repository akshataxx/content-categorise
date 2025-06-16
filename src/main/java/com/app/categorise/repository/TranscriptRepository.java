package com.app.categorise.repository;

import com.app.categorise.models.entity.Transcript;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranscriptRepository extends MongoRepository<Transcript, String>, CustomTranscriptRepository {}
