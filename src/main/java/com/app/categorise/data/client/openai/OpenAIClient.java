package com.app.categorise.data.client.openai;

import java.util.List;
import java.util.Map;

public interface OpenAIClient {
    List<String> classifyTranscript(String transcript, String title, String description);

    Map<String, String> generateAliasesForCategories(List<String> canonicalCategories);
}
