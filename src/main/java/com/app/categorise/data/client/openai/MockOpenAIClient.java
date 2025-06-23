package com.app.categorise.data.client.openai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@Profile("dev")
public class MockOpenAIClient implements OpenAIClient {

    @Override
    public List<String> classifyTranscript(String transcript, String title, String description) {
        System.out.println("Mocking OpenAI categorization");
        return Arrays.asList("recipes", "meal prep", "budget cooking");
    }

    @Override
    public Map<String, String> generateAliasesForCategories(List<String> categories) {
        System.out.println("Mocking OpenAI category aliases");
        return Map.of(
            "recipes", "cooking",
            "meal prep", "meal planning",
            "budget cooking", "affordable meals"
        );
    }
}
