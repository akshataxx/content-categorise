package com.app.categorise.domain.service;

import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.UserSubcategoryEntity;
import com.app.categorise.data.repository.UserSubcategoryRepository;
import com.app.categorise.exception.SubcategoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserSubcategoryService {

    private final CategoryService categoryService;
    private final UserSubcategoryRepository userSubcategoryRepository;

    public UserSubcategoryService(CategoryService categoryService,
                                  UserSubcategoryRepository userSubcategoryRepository) {
        this.categoryService = categoryService;
        this.userSubcategoryRepository = userSubcategoryRepository;
    }

    @Transactional
    public UserSubcategoryEntity createSubcategory(UUID userId, UUID parentId, String name, String description) {
        validateUserId(userId);
        String normalisedName = normaliseName(name);
        CategoryEntity parent = categoryService.findCategoryById(parentId)
            .orElseThrow(() -> new IllegalArgumentException("Parent category does not exist"));

        ensureNameIsAvailable(userId, parentId, normalisedName, null);

        return userSubcategoryRepository.save(
            new UserSubcategoryEntity(userId, parent, normalisedName, description)
        );
    }

    @Transactional
    public UserSubcategoryEntity updateSubcategory(UUID userId, UUID subcategoryId, String name, String description) {
        UserSubcategoryEntity entity = getOwnedSubcategory(userId, subcategoryId)
            .orElseThrow(() -> new SubcategoryNotFoundException("Subcategory not found: " + subcategoryId));

        if (name != null) {
            String normalisedName = normaliseName(name);
            ensureNameIsAvailable(userId, entity.getParentId(), normalisedName, entity.getId());
            entity.setName(normalisedName);
        }
        if (description != null) {
            entity.setDescription(description);
        }
        return userSubcategoryRepository.save(entity);
    }

    @Transactional
    public void deleteSubcategory(UUID userId, UUID subcategoryId) {
        UserSubcategoryEntity entity = getOwnedSubcategory(userId, subcategoryId)
            .orElseThrow(() -> new SubcategoryNotFoundException("Subcategory not found: " + subcategoryId));
        userSubcategoryRepository.delete(entity);
    }

    public List<UserSubcategoryEntity> findByUser(UUID userId) {
        validateUserId(userId);
        return userSubcategoryRepository.findByUserId(userId);
    }

    public List<UserSubcategoryEntity> findByUserAndParent(UUID userId, UUID parentId) {
        validateUserId(userId);
        return userSubcategoryRepository.findByUserIdAndParent_Id(userId, parentId);
    }

    public Optional<UserSubcategoryEntity> getOwnedSubcategory(UUID userId, UUID subcategoryId) {
        validateUserId(userId);
        if (subcategoryId == null) {
            return Optional.empty();
        }
        return userSubcategoryRepository.findByIdAndUserId(subcategoryId, userId);
    }

    private void ensureNameIsAvailable(UUID userId, UUID parentId, String name, UUID currentId) {
        userSubcategoryRepository.findByUserIdAndParent_IdAndNameIgnoreCase(userId, parentId, name)
            .filter(existing -> currentId == null || !existing.getId().equals(currentId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Subcategory name already exists under this parent category");
            });
    }

    private String normaliseName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Subcategory name cannot be blank");
        }
        return name.trim();
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
    }
}
