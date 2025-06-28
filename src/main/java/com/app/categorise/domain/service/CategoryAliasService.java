package com.app.categorise.domain.service;

import com.app.categorise.data.entity.CategoryAliasEntity;
import com.app.categorise.data.repository.CategoryAliasRepository;
import com.app.categorise.data.repository.TranscriptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing category aliases for users.
 * This service provides methods to retrieve and save category aliases to db.
 */

@Service
public class CategoryAliasService {

    private final CategoryAliasRepository aliasRepository;
    private final TranscriptRepository transcriptRepository;

    public CategoryAliasService(CategoryAliasRepository aliasRepository, TranscriptRepository transcriptRepository) {
        this.aliasRepository = aliasRepository;
        this.transcriptRepository = transcriptRepository;
    }

    /**
     * Finds a user's specific alias preference for a given grouping key.
     * @param userId The user's ID.
     * @param groupingKey The grouping key (e.g., "Recipe", "tech").
     * @return An Optional containing the {@link CategoryAliasEntity} if a preference exists.
     */
    public Optional<CategoryAliasEntity> findByUserIdAndGroupingKey(String userId, String groupingKey) {
        return aliasRepository.findByUserIdAndGroupingKey(userId, groupingKey);
    }

    /**
     * Retrieves a map of category aliases for a given user.
     * @param userId
     * @return A map where keys are grouping keys and values are the user's preferred aliases.
     */
    public Map<String, String> getAliasesForUser(String userId) {
        return aliasRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        CategoryAliasEntity::getGroupingKey,
                        CategoryAliasEntity::getAlias
                ));
    }

    /**
     * Saves a new alias preference for a user.
     * @param userId The user's ID.
     * @param groupingKey The grouping key to associate the alias with.
     * @param alias The alias to save.
     */
    public void saveAlias(String userId, String groupingKey, String alias) {
        CategoryAliasEntity aliasEntity = new CategoryAliasEntity();
        aliasEntity.setUserId(userId);
        aliasEntity.setGroupingKey(groupingKey);
        aliasEntity.setAlias(alias);
        aliasRepository.save(aliasEntity);
    }

    /**
     * Renames an alias for a user. This is a transactional operation that updates the user's
     * future preference and also performs a bulk update on all existing transcripts to reflect the change.
     * @param userId The user's ID.
     * @param groupingKey The grouping key whose alias is being renamed.
     * @param newAlias The new alias name.
     */
    @Transactional
    public void renameAlias(String userId, String groupingKey, String newAlias) {
        // Step 1: Update or create the user's preference for future videos
        CategoryAliasEntity aliasEntity = aliasRepository.findByUserIdAndGroupingKey(userId, groupingKey)
                .orElse(new CategoryAliasEntity());

        aliasEntity.setUserId(userId);
        aliasEntity.setGroupingKey(groupingKey);
        aliasEntity.setAlias(newAlias);
        aliasRepository.save(aliasEntity);

        // Step 2: Bulk-update all existing transcripts for this user and category
        transcriptRepository.updateAliasForUserAndGroupingKey(userId, groupingKey, newAlias);
    }

    /**
     * Saves AI-generated aliases for a given user.
     * @param userId
     * @param aliasMap
     */
    @Deprecated
    public void saveAliases(String userId, Map<String, String> aliasMap) {
        List<CategoryAliasEntity> aliases = aliasMap.entrySet().stream()
                .map(entry -> {
                    CategoryAliasEntity alias = new CategoryAliasEntity();
                    alias.setUserId(userId);
                    alias.setGroupingKey(entry.getKey());
                    alias.setAlias(entry.getValue());
                    return alias;
                })
                .toList();

        aliasRepository.saveAll(aliases);
    }

}
