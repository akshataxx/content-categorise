package com.app.categorise.repository;

import com.app.categorise.entity.Transcript;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranscriptRepository extends MongoRepository<Transcript, Long> {

    Transcript findByVideoUrl(String videoUrl);

    List<Transcript> findByCategoriesContaining(String category);
}