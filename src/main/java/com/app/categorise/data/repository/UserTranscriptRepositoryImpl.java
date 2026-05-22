package com.app.categorise.data.repository;

import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class UserTranscriptRepositoryImpl implements CustomUserTranscriptRepository {

    private final EntityManager entityManager;

    public UserTranscriptRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<UserTranscriptEntity> filterByUser(UUID userId, List<UUID> categories, String account, Instant from, Instant to) {
        return filterByUser(userId, categories, null, account, from, to);
    }

    @Override
    public List<UserTranscriptEntity> filterByUser(UUID userId, List<UUID> categories, List<UUID> subcategories, String account, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UserTranscriptEntity> query = cb.createQuery(UserTranscriptEntity.class);
        Root<UserTranscriptEntity> root = query.from(UserTranscriptEntity.class);

        // Join baseTranscript for filtering on its fields (eager loading handles data fetching)
        Join<UserTranscriptEntity, BaseTranscriptEntity> baseTranscriptJoin = root.join("baseTranscript", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get("userId"), userId));

        if (categories != null && !categories.isEmpty()) {
            predicates.add(root.get("category").get("id").in(categories));
        }

        if (subcategories != null && !subcategories.isEmpty()) {
            predicates.add(root.get("userSubcategory").get("id").in(subcategories));
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

    @Override
    @SuppressWarnings("unchecked")
    public List<UserTranscriptEntity> searchByEmbedding(UUID userId, float[] queryEmbedding, String queryText, int limit, UUID categoryId) {
        String vectorStr = toVectorString(queryEmbedding);

        String sql = """
                WITH search_query AS (
                    SELECT
                        CAST(:queryVector AS vector) AS query_vector,
                        websearch_to_tsquery('english', :queryText) AS text_query
                ),
                ranked AS (
                    SELECT
                        ut.id,
                        CASE
                            WHEN bt.embedding IS NOT NULL THEN bt.embedding <=> search_query.query_vector
                            ELSE 2.0
                        END AS vector_distance,
                        ts_rank_cd(
                            to_tsvector(
                                'english',
                                concat_ws(
                                    ' ',
                                    bt.title,
                                    bt.generated_title,
                                    bt.description,
                                    bt.structured_content::text,
                                    bt.transcript
                                )
                            ),
                            search_query.text_query
                        ) AS text_rank,
                        CASE
                            WHEN concat_ws(
                                ' ',
                                bt.title,
                                bt.generated_title,
                                bt.description,
                                bt.structured_content::text,
                                bt.transcript,
                                c.name
                            ) ILIKE '%' || :queryText || '%' THEN 0.15
                            ELSE 0.0
                        END AS exact_match_boost
                    FROM user_transcripts ut
                    JOIN base_transcripts bt ON ut.base_transcript_id = bt.id
                    LEFT JOIN categories c ON ut.category_id = c.id
                    CROSS JOIN search_query
                    WHERE ut.user_id = CAST(:userId AS uuid)
                      AND (
                          bt.embedding IS NOT NULL
                          OR to_tsvector(
                              'english',
                              concat_ws(
                                  ' ',
                                  bt.title,
                                  bt.generated_title,
                                  bt.description,
                                  bt.structured_content::text,
                                  bt.transcript
                              )
                          ) @@ search_query.text_query
                      )
                """ + (categoryId != null ? "      AND ut.category_id = CAST(:categoryId AS uuid)\n" : "") + """
                )
                SELECT id::text
                FROM ranked
                ORDER BY
                    vector_distance - LEAST(text_rank * 0.25, 0.35) - exact_match_boost,
                    vector_distance
                LIMIT :limit
                """;

        var query = entityManager.createNativeQuery(sql)
            .setParameter("userId", userId.toString())
            .setParameter("queryVector", vectorStr)
            .setParameter("queryText", queryText)
            .setParameter("limit", limit);

        if (categoryId != null) {
            query.setParameter("categoryId", categoryId.toString());
        }

        List<UUID> orderedIds = query
            .getResultList()
            .stream()
            .map(r -> UUID.fromString(r.toString()))
            .toList();

        if (orderedIds.isEmpty()) return List.of();

        Map<UUID, UserTranscriptEntity> byId = entityManager
            .createQuery(
                "SELECT ut FROM UserTranscriptEntity ut WHERE ut.id IN :ids AND ut.userId = :userId",
                UserTranscriptEntity.class)
            .setParameter("ids", orderedIds)
            .setParameter("userId", userId)
            .getResultList()
            .stream()
            .collect(Collectors.toMap(UserTranscriptEntity::getId, ut -> ut));

        return orderedIds.stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .toList();
    }

    private static String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
