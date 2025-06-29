package com.app.categorise.data.repository;

import com.app.categorise.data.entity.TranscriptEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class TranscriptRepositoryImpl implements CustomTranscriptRepository {

    private final EntityManager entityManager;

    public TranscriptRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<TranscriptEntity> filter(List<UUID> categories, String account, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TranscriptEntity> query = cb.createQuery(TranscriptEntity.class);
        Root<TranscriptEntity> root = query.from(TranscriptEntity.class);

        List<Predicate> predicates = new ArrayList<>();

        if (categories != null && !categories.isEmpty()) {
            predicates.add(root.get("categoryId").in(categories));
        }

        if (account != null && !account.isBlank()) {
            predicates.add(cb.equal(root.get("account"), account));
        }

        if (from != null && to != null) {
            predicates.add(cb.between(root.get("uploadedAt"), from, to));
        } else if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("uploadedAt"), from));
        } else if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("uploadedAt"), to));
        }

        query.where(cb.and(predicates.toArray(new Predicate[0])));
        return entityManager.createQuery(query).getResultList();
    }
}


