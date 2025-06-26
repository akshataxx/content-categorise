package com.app.categorise.data.repository;

import com.app.categorise.data.entity.CategoryEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link CategoryEntity}
 */
@Repository
public interface CategoryRepository extends MongoRepository<CategoryEntity, String> {
} 