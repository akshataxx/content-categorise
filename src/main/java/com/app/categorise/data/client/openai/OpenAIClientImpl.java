package com.app.categorise.data.client.openai;

import com.fasterxml.jackson.core.type.TypeReference;
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
    public List<String> classifyTranscript(String transcriptEntity, String title, String description) {
        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = createClassifyTranscriptRequest(transcriptEntity, title, description);

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
                    .map(String::toLowerCase)
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("Error classifying transcriptEntity via OpenAI", e);
        }
    }

    public Map<String, String> generateAliasesForCategories(List<String> canonicalCategories) {
        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String systemPrompt = """
                You are a social media trend expert who helps apps generate fun and trendy aliases for generic content categories.
                        You will be given a list of canonical categories like "recipe", "makeup", or "lifestyle".
                        Return a JSON map where each canonical category is mapped to a short, fun, trendy display name.
                        Make sure each alias is unique, catchy, and less than 3 words. Return only the JSON object, nothing else.
                        Example:
                        { "recipe": "Big-Back", "makeup": "Glow Up", "lifestyle": "Clean Girl Era" }
                """;

        String joined = String.join(",", canonicalCategories);
        String userPrompt = String.format("Categories: %s ", joined);

        Map<String, Object> body = Map.of(
                "model", "gpt-3.5-turbo",
                "temperature", 0.2,
                "messages", new Object[]{
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                }
        );

        try{
            String response = restClient.post()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            System.out.println("OpenAI response for aliases: " + response);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response);
            String content = json.path("choices").get(0).path("message").path("content").asText();
            return mapper.readValue(content, new TypeReference<Map<String, String>>() {});

        } catch (Exception e) {
            throw new RuntimeException("Error generating aliases via OpenAI", e);
        }


    }

    private static Map<String, Object> createClassifyTranscriptRequest(String transcriptEntity, String title, String description) {
        String systemPrompt = """
            Only use categories from this approved list: recipes, vegetarian, vegan, meal prep, cooking hacks, food trends, dieting, nutrition, budgeting, investing, fitness, skincare, makeup, fashion, outfit ideas, entertainment, date night, restaurant reviews, gossip, movies, TV, music, tech reviews, gadgets, productivity, travel, travel hacks, life hacks, relationships, parenting, pets, cleaning, organization, home decor, education, study tips, mental health, motivation, career advice, job search, software, ai, crypto.
            
            Return only the category list as a comma-separated string, with no explanation or extra text.
            """;

        String userPrompt = String.format("""
            You are given the following video metadata:
            
            Transcript:
            %s
            
            Title:
            %s
            
            Description:
            %s
            
            ---
            Return:
            """, transcriptEntity, title, description);

        return Map.of(
                "model", "gpt-3.5-turbo",
                "temperature", 0.2,
                "messages", new Object[]{
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                }
        );
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
