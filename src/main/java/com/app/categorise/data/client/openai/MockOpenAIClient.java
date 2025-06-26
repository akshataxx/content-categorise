package com.app.categorise.data.client.openai;

import com.app.categorise.domain.model.ClassificationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@Profile("dev")
public class MockOpenAIClient implements OpenAIClient {

    @Deprecated
    @Override
    public List<String> classifyTranscript(String transcript, String title, String description) {
        System.out.println("Mocking OpenAI categorization (DEPRECATED)");
        return List.of();
    }

    @Deprecated
    @Override
    public Map<String, String> generateAliasesForCategories(List<String> categories) {
        System.out.println("Mocking OpenAI category aliases (DEPRECATED)");
        return Map.of();
    }

    @Override
    public ClassificationResult classifyAndSuggestAlias(String transcript, String title, String description, List<String> canonicalCategoryNames) {
        System.out.println("--- MOCK classifyAndSuggestAlias ---");
        System.out.println("Title: " + title);

        if (title.toLowerCase().contains("recipe") || description.toLowerCase().contains("cooking")) {
            System.out.println("Result: Matched 'Recipe'");
            return new ClassificationResult("Recipe", "food", "Mock-Chef-Mode");
        }
        if (title.toLowerCase().contains("tech") || description.toLowerCase().contains("gadget")) {
            System.out.println("Result: Matched 'tech'");
            return new ClassificationResult(null, "tech", "Mock-Tech-Tok");
        }

        System.out.println("Result: Default match");
        return new ClassificationResult(null, "general", "Mock-Cool-Vibes");
    }
}
