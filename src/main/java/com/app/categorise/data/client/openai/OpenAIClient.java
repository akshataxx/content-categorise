package com.app.categorise.data.client.openai;

import java.util.List;
import java.util.Map;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.domain.model.ClassificationResult;

public interface OpenAIClient {
    List<String> classifyTranscript(String transcript, String title, String description);

    Map<String, String> generateAliasesForCategories(List<String> canonicalCategories);

    ClassificationResult classifyAndSuggestAlias(String transcript, String title, String description, List<String> canonicalCategoryNames);
}
