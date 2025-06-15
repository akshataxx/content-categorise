package com.app.categorise.client.openai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Profile("dev")
public class MockOpenAIClient implements OpenAIClient {

    @Override
    public List<String> classifyTranscript(String transcript, String title, String description) {
        System.out.println("Mocking OpenAI categorization");
        return Arrays.asList("recipes", "meal prep", "budget cooking");
    }
}
