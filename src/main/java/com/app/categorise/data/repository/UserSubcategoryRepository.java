package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserSubcategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubcategoryRepository extends JpaRepository<UserSubcategoryEntity, UUID> {
    List<UserSubcategoryEntity> findByUserId(UUID userId);

    List<UserSubcategoryEntity> findByUserIdAndParent_Id(UUID userId, UUID parentId);

    Optional<UserSubcategoryEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserSubcategoryEntity> findByUserIdAndParent_IdAndNameIgnoreCase(UUID userId, UUID parentId, String name);

    /**
     * Delete all subcategories belonging to a user (e.g. on account deletion).
     */
    @Modifying
    @Transactional
    void deleteByUserId(UUID userId);
}
