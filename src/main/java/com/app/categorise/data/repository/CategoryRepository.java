package com.app.categorise.data.repository;

import com.app.categorise.data.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {
    Optional<CategoryEntity> findByName(String name);

    @Query("SELECT c FROM CategoryEntity c WHERE c.createdBy IS NULL")
    List<CategoryEntity> findAllByCreatedByIsNull();
}