package com.app.categorise.domain.service;

import com.app.categorise.data.entity.CategoryAliasEntity;
import com.app.categorise.data.repository.CategoryAliasRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing category aliases for users.
 * This service provides methods to retrieve and save category aliases to db.
 */

@Service
public class CategoryAliasService {

    private final CategoryAliasRepository aliasRepository;

    public CategoryAliasService(CategoryAliasRepository aliasRepository) {
        this.aliasRepository = aliasRepository;
    }

    /**
     * Retrieves a map of category aliases for a given user.
     * @param userId
     */

    public Map<String, String> getAliasesForUser(String userId) {
        return aliasRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        CategoryAliasEntity::getCanonicalCategory,
                        CategoryAliasEntity::getAlias
                ));
    }

    /**
     * Saves AI-generated aliases for a given user.
     * @param userId
     * @param aliasMap
     */

    public void saveAliases(String userId, Map<String, String> aliasMap) {
        List<CategoryAliasEntity> aliases = aliasMap.entrySet().stream()
                .map(entry -> {
                    CategoryAliasEntity alias = new CategoryAliasEntity();
                    alias.setUserId(userId);
                    alias.setCanonicalCategory(entry.getKey());
                    alias.setAlias(entry.getValue());
                    return alias;
                })
                .toList();

        aliasRepository.saveAll(aliases);
    }



}
