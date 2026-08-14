package com.app.categorise.data.client.tiktok;

public final class TikTokEmbedFallbackException extends RuntimeException {
    public TikTokEmbedFallbackException(String reason) {
        super("TikTok embed fallback failed: " + reason);
    }

}
