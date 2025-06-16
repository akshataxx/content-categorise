package com.app.categorise.repository;

import com.app.categorise.models.entity.Transcript;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class TranscriptRepositoryImpl implements CustomTranscriptRepository {

    private final MongoTemplate mongoTemplate;

    public TranscriptRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Transcript> filter(List<String> categories, String account, Instant from, Instant to) {
        Query query = new Query();

        if (categories != null && !categories.isEmpty()) {
            query.addCriteria(Criteria.where("categories").in(categories));
        }

        if (account != null && !account.isEmpty()) {
            query.addCriteria(Criteria.where("account").is(account));
        }

        if (from != null && to != null) {
            query.addCriteria(Criteria.where("uploadedAt").gte(from).lte(to));
        } else if (from != null) {
            query.addCriteria(Criteria.where("uploadedAt").gte(from));
        } else if (to != null) {
            query.addCriteria(Criteria.where("uploadedAt").lte(to));
        }

        return mongoTemplate.find(query, Transcript.class);
    }
}

