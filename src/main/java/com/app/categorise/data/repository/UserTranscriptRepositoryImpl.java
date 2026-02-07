package com.app.categorise.data.repository;

import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class UserTranscriptRepositoryImpl implements CustomUserTranscriptRepository {

    private final EntityManager entityManager;

    public UserTranscriptRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UserTranscriptEntity> filterByUser(UUID userId, List<UUID> categories, String account, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UserTranscriptEntity> query = cb.createQuery(UserTranscriptEntity.class);
        Root<UserTranscriptEntity> root = query.from(UserTranscriptEntity.class);

        // Fetch join baseTranscript and category to avoid lazy loading
        Join<UserTranscriptEntity, BaseTranscriptEntity> baseTranscriptJoin =
            (Join<UserTranscriptEntity, BaseTranscriptEntity>) (Join<?, ?>) root.fetch("baseTranscript", JoinType.INNER);
        root.fetch("category", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();

        // Always filter by userId
        predicates.add(cb.equal(root.get("userId"), userId));

        if (categories != null && !categories.isEmpty()) {
            predicates.add(root.get("category").get("id").in(categories));
        }

        if (account != null && !account.isBlank()) {
            predicates.add(cb.equal(baseTranscriptJoin.get("account"), account));
        }

        if (from != null && to != null) {
            predicates.add(cb.between(baseTranscriptJoin.get("uploadedAt"), from, to));
        } else if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(baseTranscriptJoin.get("uploadedAt"), from));
        } else if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(baseTranscriptJoin.get("uploadedAt"), to));
        }

        query.where(cb.and(predicates.toArray(new Predicate[0])));
        return entityManager.createQuery(query).getResultList();
    }
}


