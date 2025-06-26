package com.app.categorise.domain.service;

import com.app.categorise.data.client.openai.OpenAIClient;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.repository.CategoryRepository;
import com.app.categorise.domain.model.ClassificationResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for categorising video content.
 * This service uses OpenAI to classify video transcripts into high-level categories.
 * It provides a method to classify a video based on its transcript, title, and description.
 */
@Service
public class   CategorisationService {

    private final OpenAIClient openAIClient;
    private final CategoryRepository categoryRepository;

    public CategorisationService(OpenAIClient openAIClient, CategoryRepository categoryRepository) {
        this.openAIClient = openAIClient;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Classifies the given video into 2–4 high-level categories using OpenAI.
     * @param transcript The transcript of the video
     * @param title The title of the video
     * @param description The description of the video
     * @return A list of relevant categories
     */
    public List<String> classify(String transcript, String title, String description) {
        return openAIClient.classifyTranscript(transcript, title, description);
    }

    public Map<String, String> generateAliases(List<String> canonicalCategories) {
        return openAIClient.generateAliasesForCategories(canonicalCategories);
    }

    /**
     * Classifies a video's content and suggests an alias.
     * This method fetches all predefined canonical categories from the database and passes them, along with the video's
     * text content, to the OpenAI client for analysis.
     * @param transcript The video transcript.
     * @param title The video title.
     * @param description The video description.
     * @return A {@link ClassificationResult} containing the analysis from the AI.
     */
    public ClassificationResult classifyAndSuggestAlias(String transcript, String title, String description) {
        List<String> canonicalCategoryNames = categoryRepository.findAll().stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.toList());

        return openAIClient.classifyAndSuggestAlias(transcript, title, description, canonicalCategoryNames);
    }
}

