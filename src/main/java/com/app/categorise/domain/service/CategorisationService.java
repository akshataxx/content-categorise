package com.app.categorise.domain.service;

import com.app.categorise.data.client.openai.OpenAIClient;
import com.app.categorise.data.dto.TranscriptCategorisationResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for categorising video content.
 * This service uses OpenAI to classify video transcripts into high-level categories.
 * It provides a method to classify a video based on its transcript, title, and description.
 */
@Service
public class CategorisationService {

    private final OpenAIClient openAIClient;
    private final CategoryService categoryService;

    public CategorisationService(OpenAIClient openAIClient, CategoryService categoryService) {
        this.openAIClient = openAIClient;
        this.categoryService = categoryService;
    }

    /**
     * Classifies a video's content and suggests an alias.
     * This method fetches all predefined canonical categories from the database and passes them, along with the video's
     * text content, to the OpenAI client for analysis.
     * @param transcript The video transcript.
     * @param title The video title.
     * @param description The video description.
     * @return A {@link TranscriptCategorisationResult} containing the analysis from the AI.
     */
    public TranscriptCategorisationResult classifyAndSuggestAlias(String transcript, String title, String description) {
        // Fetch all predefined categories not created by the user
        List<String> categoryNames = categoryService.getAllRootCategoryNames();

        return openAIClient.classifyAndSuggestAlias(transcript, title, description, categoryNames);
    }
}
