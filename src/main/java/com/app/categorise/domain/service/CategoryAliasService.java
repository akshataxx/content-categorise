package com.app.categorise.domain.service;

import com.app.categorise.data.entity.CategoryAliasEntity;
import com.app.categorise.data.repository.CategoryAliasRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing category aliases for users.
 * This service provides methods to retrieve and save categoryId aliases to db.
 */

@Service
public class CategoryAliasService {

    private final CategoryAliasRepository aliasRepository;
    private final CategoryService categoryService;

    public CategoryAliasService(
        CategoryAliasRepository aliasRepository,
        CategoryService categoryService
    ) {
        this.aliasRepository = aliasRepository;
        this.categoryService = categoryService;
    }

    /**
     * Finds a user's specific alias preference for a given grouping key.
     * @param userId The user's ID.
     * @param categoryId The categoryId name (e.g., "Recipe", "Tech").
     * @return An Optional containing the {@link CategoryAliasEntity} if a preference exists.
     */
    public Optional<CategoryAliasEntity> findByUserIdAndCategoryId(String userId, String categoryId) {
        return aliasRepository.findByUserIdAndCategoryId(userId, categoryId);
    }

    /**
     * Retrieves a map of categoryId aliases for a given user.
     * @param userId
     * @return A map where keys are grouping keys and values are the user's preferred aliases.
     */
    public Map<String, String> getAliasesForUser(String userId) {
        return aliasRepository.findByUserId(userId).stream()
            .collect(Collectors.toMap(
                CategoryAliasEntity::getCategoryId,
                CategoryAliasEntity::getAlias
            ));
    }

    /**
     * Saves a new alias preference for a user.
     * @param userId The user's ID.
     * @param categoryId The categoryId to associate the alias with.
     * @param alias The alias to save.
     */
    public void saveAlias(String userId, String categoryId, String alias) {
        CategoryAliasEntity aliasEntity = new CategoryAliasEntity();
        aliasEntity.setUserId(userId);
        aliasEntity.setCategoryId(categoryId);
        aliasEntity.setAlias(alias);
        aliasRepository.save(aliasEntity);
    }

    /**
     * Renames an alias for a user. This is a transactional operation that updates the user's
     * future preference and also performs a bulk update on all existing transcripts to reflect the change.
     * @param userId The user's ID.
     * @param categoryId The categoryId whose alias is being renamed.
     * @param newAlias The new alias name.
     */
    @Transactional
    public CategoryAliasEntity upsertAlias(String userId, String categoryId, String newAlias) throws Exception {
        if (categoryService.findCategoryById(categoryId).isEmpty()) {
            throw new Exception("Category does not exist");
        }

        CategoryAliasEntity aliasEntity = aliasRepository.findByUserIdAndCategoryId(userId, categoryId)
            .orElse(new CategoryAliasEntity());

        aliasEntity.setUserId(userId);
        aliasEntity.setCategoryId(categoryId);
        aliasEntity.setAlias(newAlias);
        return aliasRepository.save(aliasEntity);
    }
}
