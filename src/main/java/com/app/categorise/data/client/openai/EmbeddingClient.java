package com.app.categorise.data.client.openai;

public interface EmbeddingClient {
    float[] embed(String text);
}
