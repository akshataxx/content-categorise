package com.app.categorise.data.client.openai;

import com.app.categorise.domain.dto.TranscriptCategorisationResult;
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
            return new TranscriptCategorisationResult("Recipe", "food", "Mock-Chef-Mode");
        }
        if (title.toLowerCase().contains("tech") || description.toLowerCase().contains("gadget")) {
            System.out.println("Result: Matched 'tech'");
            return new TranscriptCategorisationResult(null, "tech", "Mock-Tech-Tok");
        }

        System.out.println("Result: Default match");
        return new TranscriptCategorisationResult(null, "general", "Mock-Cool-Vibes");
    }
}
