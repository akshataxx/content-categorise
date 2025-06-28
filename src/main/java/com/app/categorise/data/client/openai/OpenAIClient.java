package com.app.categorise.data.client.openai;

import com.app.categorise.domain.dto.TranscriptCategorisationResult;

import java.util.List;

public interface OpenAIClient {

    TranscriptCategorisationResult classifyAndSuggestAlias(String transcript, String title, String description, List<String> categoryNames);
}
