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

        return "You are an AI that classifies short-form social media videos (e.g., TikToks, YouTube Shorts, Instagram Reels). You are given the title, description, and transcript of a video. Respond ONLY with a valid JSON object with **exactly** four keys: 'categoryId', 'genericTopic', 'suggestedAlias', and 'generatedTitle'.\n" +
            "1. 'categoryId': If the content primarily belongs to one of the following special categories, ["
            + categoryList +
            "], provide that categoryId name. Otherwise, this MUST be null.\n" +
            "2. \"genericTopic\": Return a single, lowercase, one-word keyword that describes the overall topic (e.g., \"tech\", \"fashion\", \"comedy\", \"health\"). This field MUST always be present.\n" +
            "3. 'suggestedAlias': Create a trendy, engaging, and short (1-3 words) alias for the video. This alias should be catchy and follow recent trends in social media. Make sure not be cringe. Make sure it isn't specific to the video. It's supposed to be an alias for the categoryId \n" +
            "4. 'generatedTitle': Generate a short, engaging title (max 60 characters) that captures the tone and main point of the video. Base this on the transcript and description content, NOT the original title. Think of it as a headline for a phone screen — punchy, clear, and true to the vibe of the video. This field MUST always be present.\n\n" +
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
            String generatedTitle = root.path("generatedTitle").asText(null);
            return new TranscriptCategorisationResult(category, topic, alias, generatedTitle);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    @Override
    public String extractStructuredContent(String transcript, String title, String category, String description) {
        String prompt = buildStructuredContentPrompt(transcript, title, category, description);
        String response = callOpenAI("You are a helpful assistant that extracts structured information from video transcripts.", prompt);
        return cleanJsonResponse(response);
    }

    private String buildStructuredContentPrompt(String transcript, String title, String category, String description) {
        // Determine content type based on category
        boolean isCooking = category != null && (
            category.equalsIgnoreCase("Cooking") ||
            category.equalsIgnoreCase("Recipe") ||
            category.equalsIgnoreCase("Recipes") ||  // Support plural form
            category.equalsIgnoreCase("Food")
        );

        boolean isBeauty = category != null && (
            category.equalsIgnoreCase("Skincare") ||
            category.equalsIgnoreCase("Makeup") ||
            category.equalsIgnoreCase("Beauty")
        );

        if (isCooking) {
            return buildCookingPrompt(transcript, title, description);
        } else if (isBeauty) {
            return buildBeautyPrompt(transcript, title);
        } else {
            return buildGeneralPrompt(transcript, title);
        }
    }

    private String buildCookingPrompt(String transcript, String title, String description) {
        return "Extract recipe information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Description: " + description + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"recipe\",\n" +
            "  \"ingredients\": [\"ingredient 1\", \"ingredient 2\"],\n" +
            "  \"steps\": [\"step 1\", \"step 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Extract all ingredients with quantities\n" +
            "- Break down recipe into clear, numbered steps\n" +
            "- If no recipe found, return empty arrays\n" +
            "- Return ONLY valid JSON, no explanations\n" +
            "- If the transcript is unrelated to food, usually that's because a song is playing over the video, " +
                "discard transcript information in this case\n" +
            "- If you cannot find a recipe in the provided information, create one for the dish referenced in the " +
                "transcript, title or description\n";
    }

    private String buildBeautyPrompt(String transcript, String title) {
        return "Extract beauty/skincare routine information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"beauty\",\n" +
            "  \"products\": [\"product 1\", \"product 2\"],\n" +
            "  \"steps\": [\"step 1\", \"step 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Extract all products/brands mentioned\n" +
            "- Break down routine into clear steps\n" +
            "- If no specific routine, return empty arrays\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String buildGeneralPrompt(String transcript, String title) {
        return "Extract key points from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"general\",\n" +
            "  \"keyPoints\": [\"point 1\", \"point 2\", \"point 3\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Extract 5-10 most important points\n" +
            "- Keep each point concise (1-2 sentences max)\n" +
            "- Focus on actionable takeaways or main ideas\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String cleanJsonResponse(String response) {
        // Remove markdown code blocks if present
        if (response.contains("```json")) {
            response = response.substring(response.indexOf("```json") + 7);
            response = response.substring(0, response.indexOf("```"));
        } else if (response.contains("```")) {
            response = response.substring(response.indexOf('{'), response.lastIndexOf('}') + 1);
        }
        return response.trim();
    }
}
