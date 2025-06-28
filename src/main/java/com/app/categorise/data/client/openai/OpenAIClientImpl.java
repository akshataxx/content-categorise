package com.app.categorise.data.client.openai;

import com.app.categorise.data.dto.TranscriptCategorisationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

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

    @Override
    public TranscriptCategorisationResult classifyAndSuggestAlias(
        String transcript,
        String title,
        String description,
        List<String> categoryNames
    ) {
        String developerPrompt = buildDeveloperPrompt(categoryNames);
        String userPrompt = buildUserPrompt(transcript, title, description);
        String response = callOpenAI(developerPrompt, userPrompt);
        return parseResponse(response);
    }

    public String buildDeveloperPrompt(List<String> categories) {
        String categoryList = String.join(", ", categories.stream().map(c -> "\"" + c + "\"").toArray(String[]::new));

        return "You are an AI that classifies short-form social media videos (e.g., TikToks). You are given the title, description, and transcript of a video. Respond ONLY with a valid JSON object with  **exactly** three keys: 'categoryId', 'genericTopic', and 'suggestedAlias'.\n" +
            "1. 'categoryId': If the content primarily belongs to one of the following special categories, ["
            + categoryList +
            "], provide that categoryId name. Otherwise, this MUST be null.\n" +
            "2. \"genericTopic\": Return a single, lowercase, one-word keyword that describes the overall topic (e.g., \"tech\", \"fashion\", \"comedy\", \"health\"). This field MUST always be present.\n" +
            "3. 'suggestedAlias': Create a trendy, engaging, and short (1-3 words) alias for the video. This alias should be catchy and follow recent trends in social media. Make sure not be cringe. Make sure it isn't specific to the video. It's supposed to be an alias for the categoryId \n\n" +
            "DO NOT include any explanation or extra text. Just output the JSON object.\n\n";
    }

    private String buildUserPrompt(
        String transcript,
        String title,
        String description
    ) {
        return "Title: " + title + "\n" +
            "Description: " + description + "\n" +
            "Transcript: " + transcript;
    }

    private String callOpenAI(String developerPrompt, String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ObjectNode developerMessage = objectMapper.createObjectNode();
        developerMessage.put("role", "developer");
        developerMessage.put("content", developerPrompt);

        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(developerMessage);
        messages.add(userMessage);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "gpt-4o");
        requestBody.set("messages", messages);
        requestBody.put("temperature", 0.7);

        try {
            String response = restClient.post()
                .uri(apiUrl + "/v1/chat/completions")
                .headers(h -> h.addAll(headers))
                .body(requestBody)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to call OpenAI API", e);
        }
    }

    /**
     * Parses the JSON response string from OpenAI into a structured ClassificationResult object.
     * @param jsonResponse The raw JSON string from the API.
     * @return A {@link TranscriptCategorisationResult} object.
     */
    private TranscriptCategorisationResult parseResponse(String jsonResponse) {
        try {
            // The model sometimes wraps the JSON in Markdown code blocks (e.g., ```json ... ```)
            // We need to extract the raw JSON string.
            if (jsonResponse.contains("```")) {
                jsonResponse = jsonResponse.substring(jsonResponse.indexOf('{'), jsonResponse.lastIndexOf('}') + 1);
            }

            JsonNode root = objectMapper.readTree(jsonResponse);
            String category = root.has("categoryId") && !root.get("categoryId").isNull()
                    ? root.get("categoryId").asText()
                    : null;
            String topic = root.path("genericTopic").asText("default-topic");
            String alias = root.path("suggestedAlias").asText("default-alias");
            return new TranscriptCategorisationResult(category, topic, alias);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }
}
