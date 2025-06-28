package com.app.categorise.data.repository;

import com.app.categorise.data.entity.CategoryEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link CategoryEntity}
 */
@Repository
public interface CategoryRepository extends MongoRepository<CategoryEntity, String> {
    Optional<CategoryEntity> findByName(String name);

    @Query("{ 'createdBy': null }")
    List<CategoryEntity> findAllByCreatedByIsNull();

} 