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

        String prompt = String.format("""
            Classify this video based on the following transcript, title, and description.

            Transcript:
            %s

            Title:
            %s

            Description:
            %s

            Return only a comma-separated list of 2 to 4 relevant categories.
        """, transcript, title, description);

        Map<String, Object> body = Map.of(
                "model", "gpt-3.5-turbo",
                "temperature", 0.2,
                "messages", new Object[]{
                        Map.of("role", "system", "content", "You are a helpful assistant that classifies video content into 2 to 4 high-level categories like 'recipes', 'fitness', 'finance', 'entertainment', 'gossip', etc."),
                        Map.of("role", "user", "content", prompt)
                }
        );

        try {
            String response = restClient.post()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            System.out.println("OpenAI response: " + response);

            String categories = extractCategoriesFromResponse(response);
            return Arrays.asList(categories.split("\\s*,\\s*"));

        } catch (Exception e) {
            throw new RuntimeException("Error classifying transcript via OpenAI", e);
        }
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
