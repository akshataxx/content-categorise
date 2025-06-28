package com.app.categorise.data.client.openai;

import com.app.categorise.domain.model.ClassificationResult;

import java.util.List;

public interface OpenAIClient {

    ClassificationResult classifyAndSuggestAlias(String transcript, String title, String description, List<String> canonicalCategoryNames);
}
