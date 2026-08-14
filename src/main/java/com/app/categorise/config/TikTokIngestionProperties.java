package com.app.categorise.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.tiktok")
public class TikTokIngestionProperties {
    private final EmbedFallback embedFallback = new EmbedFallback();

    public EmbedFallback getEmbedFallback() { return embedFallback; }

    @PostConstruct
    public void validate() {
        if (embedFallback.connectTimeoutSeconds <= 0 || embedFallback.readTimeoutSeconds <= 0
            || embedFallback.maxHtmlBytes <= 0 || embedFallback.maxMediaBytes <= 0) {
            throw new IllegalArgumentException("TikTok embed fallback limits must be positive");
        }
    }

    public static class EmbedFallback {
        private boolean enabled = true;
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 30;
        private long maxHtmlBytes = 2_097_152;
        private long maxMediaBytes = 157_286_400;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
        public long getMaxHtmlBytes() { return maxHtmlBytes; }
        public void setMaxHtmlBytes(long maxHtmlBytes) { this.maxHtmlBytes = maxHtmlBytes; }
        public long getMaxMediaBytes() { return maxMediaBytes; }
        public void setMaxMediaBytes(long maxMediaBytes) { this.maxMediaBytes = maxMediaBytes; }
    }
}
