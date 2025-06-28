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
    List<CategoryAliasEntity> findByUserId(String userId);

    Optional<CategoryAliasEntity> findByUserIdAndCategoryId(String userId, String categoryId);
}
