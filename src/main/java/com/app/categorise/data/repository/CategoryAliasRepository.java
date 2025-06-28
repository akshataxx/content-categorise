package com.app.categorise.data.repository;

import com.app.categorise.data.entity.CategoryAliasEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link CategoryAliasEntity}
 */
@Repository
public interface CategoryAliasRepository extends MongoRepository<CategoryAliasEntity, String> {

    /**
     * Finds all aliases for a given user.
     * @param userId The ID of the user.
     * @return A list of category alias entities.
     */
    List<CategoryAliasEntity> findByUserId(String userId);

    /**
     * Finds a specific alias preference for a user based on a grouping key.
     * @param userId The ID of the user.
     * @param groupingKey The grouping key (e.g., "Recipe", "tech").
     * @return An optional containing the alias entity if found.
     */
    Optional<CategoryAliasEntity> findByUserIdAndGroupingKey(String userId, String groupingKey);
}
