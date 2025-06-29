package com.app.categorise.domain.service;

import com.app.categorise.data.client.openai.OpenAIClient;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for categorising video content.
 * This service uses OpenAI to classify video transcripts into high-level categories.
 * It provides a method to classify a video based on its transcript, title, and description.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(OpenAIClient openAIClient, CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Optional<CategoryEntity> findCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    public Optional<CategoryEntity> findCategoryById(UUID id) {
        return categoryRepository.findById(id);
    }

    public CategoryEntity saveCategory(String name, String description, UUID createdBy) {
        return categoryRepository.save(new CategoryEntity(name, description, createdBy));
    }

    // Save a category if it doesn't exist, otherwise return the existing one. Uniqueness is determined by name
    public CategoryEntity saveIfNotExists(String name, String description, UUID createdBy) {
        Optional<CategoryEntity> existingCategory = categoryRepository.findByName(name);
        return existingCategory.orElseGet(() ->
            categoryRepository.save(new CategoryEntity(name, description, createdBy))
        );
    }

    // Get all categories created by the system
    public List<String> getAllCategoryNamesCreatedBySystem() {
        return categoryRepository.findAllByCreatedByIsNull().stream()
            .map(CategoryEntity::getName)
            .collect(Collectors.toList());
    }


}


