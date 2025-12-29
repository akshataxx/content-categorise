package com.app.categorise.data.client.openai;

import com.app.categorise.data.dto.TranscriptCategorisationResult;

import java.util.List;

public interface OpenAIClient {

    TranscriptCategorisationResult classifyAndSuggestAlias(String transcript, String title, String description, List<String> categoryNames);

    String extractStructuredContent(String transcript, String title, String category, String description);
}
