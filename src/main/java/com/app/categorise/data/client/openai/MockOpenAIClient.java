package com.app.categorise.data.client.openai;

import com.app.categorise.data.dto.TranscriptCategorisationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev")
public class MockOpenAIClient implements OpenAIClient {

    @Override
    public TranscriptCategorisationResult classifyAndSuggestAlias(String transcript, String title, String description, List<String> categoryNames) {
        System.out.println("--- MOCK classifyAndSuggestAlias ---");
        System.out.println("Title: " + title);

        if (title.toLowerCase().contains("recipe") || description.toLowerCase().contains("cooking")) {
            System.out.println("Result: Matched 'Recipe'");
            return new TranscriptCategorisationResult("Recipe", "food", "Mock-Chef-Mode", "Mock Generated Title");
        }
        if (title.toLowerCase().contains("tech") || description.toLowerCase().contains("gadget")) {
            System.out.println("Result: Matched 'tech'");
            return new TranscriptCategorisationResult(null, "tech", "Mock-Tech-Tok", "Mock Generated Title");
        }

        System.out.println("Result: Default match");
        return new TranscriptCategorisationResult(null, "general", "Mock-Cool-Vibes", "Mock Generated Title");
    }

    @Override
    public String extractStructuredContent(String transcript, String title, String category, String description) {
        System.out.println("--- MOCK extractStructuredContent ---");
        System.out.println("Category: " + category);

        if (category != null && (category.equalsIgnoreCase("Cooking") || category.equalsIgnoreCase("Recipe"))) {
            return "{\"type\":\"recipe\",\"ingredients\":[\"2 cups flour\",\"1 egg\",\"1 cup milk\"],\"steps\":[\"Mix dry ingredients\",\"Add wet ingredients\",\"Bake at 350°F for 30 minutes\"]}";
        } else if (category != null && (category.equalsIgnoreCase("Skincare") || category.equalsIgnoreCase("Makeup"))) {
            return "{\"type\":\"beauty\",\"products\":[\"Cleanser - CeraVe\",\"Moisturizer - Cetaphil\",\"Sunscreen - La Roche-Posay\"],\"steps\":[\"Cleanse face\",\"Apply moisturizer\",\"Apply sunscreen\"]}";
        } else {
            return "{\"type\":\"general\",\"keyPoints\":[\"Main topic discussed in video\",\"Key takeaway number one\",\"Important tip mentioned\",\"Final conclusion\"]}";
        }
    }
}
