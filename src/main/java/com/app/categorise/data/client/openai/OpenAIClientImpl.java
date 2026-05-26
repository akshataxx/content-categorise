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
        boolean isCooking = category != null && (
            category.equalsIgnoreCase("Cooking") ||
            category.equalsIgnoreCase("Recipe") ||
            category.equalsIgnoreCase("Recipes") ||
            category.equalsIgnoreCase("Food")
        );
        boolean isBeauty = category != null && (
            category.equalsIgnoreCase("Skincare") ||
            category.equalsIgnoreCase("Makeup") ||
            category.equalsIgnoreCase("Beauty")
        );
        boolean isFitness = category != null && (
            category.equalsIgnoreCase("Workouts") ||
            category.equalsIgnoreCase("Sport") ||
            category.equalsIgnoreCase("Diets")
        );
        boolean isFinance = category != null && category.equalsIgnoreCase("Investing");
        boolean isTech = category != null && (
            category.equalsIgnoreCase("Coding") ||
            category.equalsIgnoreCase("Tech") ||
            category.equalsIgnoreCase("AI")
        );
        boolean isEducation = category != null && category.equalsIgnoreCase("Education");
        boolean isTravel = category != null && category.equalsIgnoreCase("Travel");
        boolean isEntertainment = category != null && (
            category.equalsIgnoreCase("Movies") ||
            category.equalsIgnoreCase("TV") ||
            category.equalsIgnoreCase("Music") ||
            category.equalsIgnoreCase("Dance") ||
            category.equalsIgnoreCase("Comedy") ||
            category.equalsIgnoreCase("Gossip")
        );
        boolean isLifestyle = category != null && (
            category.equalsIgnoreCase("Lifestyle") ||
            category.equalsIgnoreCase("Fashion") ||
            category.equalsIgnoreCase("Jewellery") ||
            category.equalsIgnoreCase("Pets") ||
            category.equalsIgnoreCase("Cars") ||
            category.equalsIgnoreCase("DIY") ||
            category.equalsIgnoreCase("Cleaning")
        );

        if (isCooking) {
            return buildCookingPrompt(transcript, title, description);
        } else if (isBeauty) {
            return buildBeautyPrompt(transcript, title);
        } else if (isFitness) {
            return buildFitnessPrompt(transcript, title, description);
        } else if (isFinance) {
            return buildFinancePrompt(transcript, title, description);
        } else if (isTech) {
            return buildTechPrompt(transcript, title, description);
        } else if (isEducation) {
            return buildEducationPrompt(transcript, title, description);
        } else if (isTravel) {
            return buildTravelPrompt(transcript, title, description);
        } else if (isEntertainment) {
            return buildEntertainmentPrompt(transcript, title, description);
        } else if (isLifestyle) {
            return buildLifestylePrompt(transcript, title, description);
        } else {
            return buildGeneralPrompt(transcript, title, description);
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
            "  \"dishName\": \"name of the dish\",\n" +
            "  \"cuisine\": \"e.g. Indian, Italian, Mexican, Japanese\",\n" +
            "  \"meal\": \"e.g. breakfast, lunch, dinner, snack, dessert\",\n" +
            "  \"tags\": [\"e.g. vegetarian, quick, spicy, healthy\"],\n" +
            "  \"ingredients\": [\"ingredient 1\", \"ingredient 2\"],\n" +
            "  \"steps\": [\"step 1\", \"step 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Extract all ingredients with quantities\n" +
            "- Break down recipe into clear, numbered steps\n" +
            "- Always infer cuisine, meal type and tags even if not explicitly stated\n" +
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
            "  \"concern\": \"e.g. acne, anti-aging, hydration, dark spots\",\n" +
            "  \"skinType\": \"e.g. oily, dry, combination, sensitive\",\n" +
            "  \"tags\": [\"e.g. morning routine, night routine, budget, luxury\"],\n" +
            "  \"products\": [\"product 1\", \"product 2\"],\n" +
            "  \"steps\": [\"step 1\", \"step 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Extract all products/brands mentioned\n" +
            "- Break down routine into clear steps\n" +
            "- Always infer skin concern, skin type and tags even if not explicitly stated\n" +
            "- If no specific routine, return empty arrays\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String buildFitnessPrompt(String transcript, String title, String description) {
        return "Extract fitness/workout information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Description: " + description + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"fitness\",\n" +
            "  \"workoutType\": \"e.g. HIIT, yoga, strength training, cardio, pilates\",\n" +
            "  \"muscleGroups\": [\"e.g. legs, core, upper body\"],\n" +
            "  \"equipment\": [\"e.g. dumbbells, resistance bands, bodyweight\"],\n" +
            "  \"difficulty\": \"e.g. beginner, intermediate, advanced\",\n" +
            "  \"duration\": \"e.g. 10 minutes, 30 minutes\",\n" +
            "  \"goal\": \"e.g. weight loss, muscle gain, flexibility, endurance\",\n" +
            "  \"tags\": [\"e.g. no equipment, home workout, morning, low impact\"],\n" +
            "  \"keyPoints\": [\"step 1\", \"step 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Always infer workoutType, muscleGroups, difficulty and goal even if not explicitly stated\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String buildFinancePrompt(String transcript, String title, String description) {
        return "Extract finance/investing information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Description: " + description + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"finance\",\n" +
            "  \"topic\": \"e.g. stock market, crypto, real estate, budgeting, saving\",\n" +
            "  \"assetType\": \"e.g. stocks, ETFs, crypto, bonds, property\",\n" +
            "  \"strategy\": \"e.g. buy and hold, dollar cost averaging, day trading\",\n" +
            "  \"riskLevel\": \"e.g. low, medium, high\",\n" +
            "  \"audience\": \"e.g. beginner, intermediate, advanced investor\",\n" +
            "  \"tags\": [\"e.g. passive income, retirement, tax, recession\"],\n" +
            "  \"keyPoints\": [\"point 1\", \"point 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Always infer topic, assetType, riskLevel and audience even if not explicitly stated\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String buildTechPrompt(String transcript, String title, String description) {
        return "Extract technology/coding information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Description: " + description + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"tech\",\n" +
            "  \"topic\": \"e.g. machine learning, web development, cybersecurity\",\n" +
            "  \"language\": \"e.g. Python, JavaScript, Java, Swift\",\n" +
            "  \"framework\": \"e.g. React, Spring Boot, TensorFlow\",\n" +
            "  \"skillLevel\": \"e.g. beginner, intermediate, advanced\",\n" +
            "  \"tags\": [\"e.g. tutorial, tips, news, comparison, review\"],\n" +
            "  \"keyPoints\": [\"point 1\", \"point 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Always infer topic, skillLevel and tags even if not explicitly stated\n" +
            "- Use null for language/framework if not applicable\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String buildEducationPrompt(String transcript, String title, String description) {
        return "Extract educational information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Description: " + description + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"education\",\n" +
            "  \"subject\": \"e.g. history, science, mathematics, psychology\",\n" +
            "  \"topic\": \"e.g. World War II, quantum physics, cognitive bias\",\n" +
            "  \"skillLevel\": \"e.g. beginner, high school, university\",\n" +
            "  \"tags\": [\"e.g. explained, theory, facts, study tips\"],\n" +
            "  \"keyPoints\": [\"point 1\", \"point 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Always infer subject, topic and skillLevel even if not explicitly stated\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String buildTravelPrompt(String transcript, String title, String description) {
        return "Extract travel information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Description: " + description + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"travel\",\n" +
            "  \"destination\": \"e.g. Bali, Paris, Japan, New York\",\n" +
            "  \"region\": \"e.g. Southeast Asia, Europe, North America\",\n" +
            "  \"tripType\": \"e.g. solo, couple, family, backpacking, luxury\",\n" +
            "  \"budget\": \"e.g. budget, mid-range, luxury\",\n" +
            "  \"activities\": [\"e.g. hiking, food tour, sightseeing, beaches\"],\n" +
            "  \"tags\": [\"e.g. travel tips, hidden gems, must-see, packing\"],\n" +
            "  \"keyPoints\": [\"tip 1\", \"tip 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Always infer destination, region and tripType even if not explicitly stated\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String buildEntertainmentPrompt(String transcript, String title, String description) {
        return "Extract entertainment information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Description: " + description + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"entertainment\",\n" +
            "  \"contentSubtype\": \"e.g. movie review, TV recap, music video, dance tutorial, comedy skit, celebrity gossip\",\n" +
            "  \"genre\": \"e.g. horror, romance, pop, hip-hop, stand-up\",\n" +
            "  \"mood\": \"e.g. funny, emotional, hype, relaxing\",\n" +
            "  \"title\": \"e.g. name of movie/show/song if mentioned\",\n" +
            "  \"artist\": \"e.g. artist or creator name if mentioned\",\n" +
            "  \"tags\": [\"e.g. review, reaction, tutorial, trending\"],\n" +
            "  \"keyPoints\": [\"point 1\", \"point 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Always infer contentSubtype, genre and mood even if not explicitly stated\n" +
            "- Use null for title/artist if not mentioned\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String buildLifestylePrompt(String transcript, String title, String description) {
        return "Extract lifestyle information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Description: " + description + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"lifestyle\",\n" +
            "  \"subcategory\": \"e.g. fashion, home, pets, cars, DIY, cleaning\",\n" +
            "  \"topic\": \"e.g. outfit ideas, dog training, car maintenance, room makeover\",\n" +
            "  \"tags\": [\"e.g. budget, aesthetic, tips, before and after, review\"],\n" +
            "  \"keyPoints\": [\"point 1\", \"point 2\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Always infer subcategory, topic and tags even if not explicitly stated\n" +
            "- Return ONLY valid JSON, no explanations";
    }

    private String buildGeneralPrompt(String transcript, String title, String description) {
        return "Extract information from this video transcript.\n\n" +
            "Title: " + title + "\n" +
            "Description: " + description + "\n" +
            "Transcript: " + transcript + "\n\n" +
            "Return ONLY a JSON object with this exact structure:\n" +
            "{\n" +
            "  \"type\": \"general\",\n" +
            "  \"topic\": \"e.g. main subject of the video\",\n" +
            "  \"tone\": \"e.g. informative, opinion, debate, motivational\",\n" +
            "  \"audience\": \"e.g. general public, young adults, professionals\",\n" +
            "  \"tags\": [\"e.g. news, opinion, tips, story, motivational\"],\n" +
            "  \"keyPoints\": [\"point 1\", \"point 2\", \"point 3\"]\n" +
            "}\n\n" +
            "Rules:\n" +
            "- Extract 5-10 most important points\n" +
            "- Always infer topic, tone, audience and tags even if not explicitly stated\n" +
            "- Keep each keyPoint concise (1-2 sentences max)\n" +
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

    @Override
    public String expandSearchQuery(String query) {
        String prompt = "Expand this short search query into a descriptive phrase for finding video content. " +
            "Be specific about the topic, category, and context. " +
            "Return only the expanded phrase, no explanation, no punctuation at the end.\n\nQuery: " + query;
        return callOpenAIFast(
            "You expand short search queries into richer, domain-specific descriptive phrases to improve semantic video search accuracy.",
            prompt
        ).trim();
    }

    private String callOpenAIFast(String developerPrompt, String prompt) {
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
        requestBody.put("model", "gpt-4o-mini");
        requestBody.set("messages", messages);
        requestBody.put("temperature", 0.3);

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
}
