package com.app.categorise.service;

import com.app.categorise.client.openai.OpenAIClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class CategorisationService {

    private final OpenAIClient openAIClient;

    public CategorisationService(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    /**
     * Classifies the given video into 2–4 high-level categories using OpenAI.
     * @param transcript The transcript of the video
     * @param title The title of the video
     * @param description The description of the video
     * @return A list of relevant categories
     */
    public List<String> classify(String transcript, String title, String description) {
        return openAIClient.classifyTranscript(transcript, title, description);
    }
}

