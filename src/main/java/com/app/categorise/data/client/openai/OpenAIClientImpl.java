package com.app.categorise.data.client.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import com.app.categorise.domain.model.ClassificationResult;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@Component
@Profile("prod")
public class OpenAIClientImpl implements OpenAIClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;

    public OpenAIClientImpl(
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.api.url}") String apiUrl,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Deprecated
    @Override
    public List<String> classifyTranscript(String transcript, String title, String description) {
        // ... this method is now deprecated
        return List.of();
    }

    @Deprecated
    @Override
    public Map<String, String> generateAliasesForCategories(List<String> canonicalCategories) {
        // ... this method is now deprecated
        return Map.of();
    }

    @Override
    public ClassificationResult classifyAndSuggestAlias(String transcript, String title, String description, List<String> canonicalCategoryNames) {
        String prompt = buildPrompt(transcript, title, description, canonicalCategoryNames);
        String response = callOpenAI(prompt);
        return parseResponse(response);
    }

    /**
     * Builds the detailed prompt for the OpenAI API call.
     * This prompt instructs the AI to return a JSON object containing a canonical category, a generic topic, and a suggested alias.
     * @param transcript The video transcript.
     * @param title The video title.
     * @param description The video description.
     * @param canonicalCategories The list of special, predefined categories to check against.
     * @return The fully constructed prompt string.
     */
    private String buildPrompt(String transcript, String title, String description, List<String> canonicalCategories) {
        String categoryList = String.join(", ", canonicalCategories.stream().map(c -> "\"" + c + "\"").toArray(String[]::new));

        return "Analyze the following video content. Respond ONLY with a JSON object with three keys: 'canonicalCategory', 'genericTopic', and 'suggestedAlias'.\n" +
                "1. 'canonicalCategory': If the content primarily belongs to one of the following special categories, ["
                + categoryList +
                "], provide that category name. Otherwise, this MUST be null.\n" +
                "2. 'genericTopic': Provide a single, stable, one-word keyword (e.g., 'tech', 'fashion', 'comedy') that describes the general topic of the video. This should always be present.\n" +
                "3. 'suggestedAlias': Create a trendy, engaging, and short (1-3 words) alias for the video. This alias should be catchy, like a hashtag.\n\n" +
                "Title: " + title + "\n" +
                "Description: " + description + "\n" +
                "Transcript: " + transcript;
    }

    private String callOpenAI(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = "{\"model\": \"gpt-4o\", \"messages\": [{\"role\": \"user\", \"content\": \"" + escapeJson(prompt) + "\"}], \"temperature\": 0.7}";

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            String response = restClient.post()
                    .uri(apiUrl + "/chat/completions")
                    .headers(h -> h.addAll(headers))
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            // In a real app, you'd want more robust error handling
            throw new RuntimeException("Failed to call OpenAI API", e);
        }
    }

    /**
     * Parses the JSON response string from OpenAI into a structured ClassificationResult object.
     * @param jsonResponse The raw JSON string from the API.
     * @return A {@link ClassificationResult} object.
     */
    private ClassificationResult parseResponse(String jsonResponse) {
        try {
            // The model sometimes wraps the JSON in markdown code blocks (e.g., ```json ... ```)
            // We need to extract the raw JSON string.
            if (jsonResponse.contains("```")) {
                jsonResponse = jsonResponse.substring(jsonResponse.indexOf('{'), jsonResponse.lastIndexOf('}') + 1);
            }

            JsonNode root = objectMapper.readTree(jsonResponse);
            String category = root.has("canonicalCategory") && !root.get("canonicalCategory").isNull()
                    ? root.get("canonicalCategory").asText()
                    : null;
            String topic = root.path("genericTopic").asText("general"); // Default topic if missing
            String alias = root.path("suggestedAlias").asText("default-alias");
            return new ClassificationResult(category, topic, alias);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
