package com.app.categorise.data.client.openai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("disabled")
public class MockEmbeddingClient implements EmbeddingClient {

    @Override
    public float[] embed(String text) {
        float[] embedding = new float[1536];
        int hash = text.hashCode();
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) Math.sin(hash + i) * 0.1f;
        }
        return embedding;
    }
}
