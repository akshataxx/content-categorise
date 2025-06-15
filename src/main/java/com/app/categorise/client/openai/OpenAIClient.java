package com.app.categorise.client.openai;

import java.util.List;

public interface OpenAIClient {
    List<String> classifyTranscript(String transcript, String title, String description);
}
