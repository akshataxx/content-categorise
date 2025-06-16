package com.app.categorise.client.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@Component
@Profile("prod")
public class OpenAIClientImpl implements OpenAIClient {

    private final RestClient restClient;
    private final String apiKey;

    public OpenAIClientImpl(RestClient restClient, @Value("${openai.api.key}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public List<String> classifyTranscript(String transcript, String title, String description) {
        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = createClassifyTranscriptRequest(transcript, title, description);

        try {
            String response = restClient.post()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            System.out.println("OpenAI response: " + response);

            String categories = extractCategoriesFromResponse(response);
            return Arrays.stream(categories.split(","))
                    .map(String::trim)
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("Error classifying transcript via OpenAI", e);
        }
    }

    private static Map<String, Object> createClassifyTranscriptRequest(String transcript, String title, String description) {
        String prompt = String.format("""
            You are given the transcript, title, and description of a short-form video (e.g., TikTok or Instagram Reel).
        
            Your task is to classify the video into **2 to 4 high-level content categories** based on the provided information.
        
            Examples of valid categories include: recipes, meal prep, budgeting, fitness, investing, entertainment, date night, restaurant reviews, dieting, nutrition, gossip, movies, TV, music.
        
            Only return the list of categories as a **single comma-separated line**, with no explanations or additional text.
        
            ---
            Transcript:
            %s
        
            Title:
            %s
        
            Description:
            %s
        
            ---
            Return:
        """, transcript, title, description);


        Map<String, Object> body = Map.of(
                "model", "gpt-3.5-turbo",
                "temperature", 0.2,
                "messages", new Object[]{
                        Map.of("role", "system", "content", "You are a helpful assistant that classifies video content into 2 to 4 high-level categories like 'recipes', 'restaurant reviews', 'date night', 'meal prep', 'budget cooking', 'dieting', 'nutrition', 'fitness', 'investing', 'budgeting', 'movies', 'tv', 'music' 'entertainment', 'gossip', etc."),
                        Map.of("role", "user", "content", prompt)
                }
        );
        return body;
    }

    private String extractCategoriesFromResponse(String responseJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseJson);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }
}
