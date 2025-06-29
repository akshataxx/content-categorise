package com.app.categorise.data.repository;

import com.app.categorise.data.entity.CategoryAliasEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryAliasRepository extends JpaRepository<CategoryAliasEntity, UUID> {
    List<CategoryAliasEntity> findByUserId(UUID userId);
    Optional<CategoryAliasEntity> findByUserIdAndCategoryId(UUID userId, UUID categoryId);
}