package com.app.categorise.domain.model;

import java.net.URI;

public enum VideoPlatform {
    YOUTUBE,
    TIKTOK,
    INSTAGRAM,
    VIMEO,
    TWITTER,
    FACEBOOK,
    REDDIT,
    TWITCH,
    DAILYMOTION,
    UNKNOWN;

    /**
     * Derives the platform from a video URL by matching the hostname.
     * Returns UNKNOWN if the URL is null, malformed, or unrecognized.
     */
    public static VideoPlatform fromUrl(String url) {
        if (url == null || url.isBlank()) return UNKNOWN;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return UNKNOWN;
            host = normalizeHost(host);

            if (matchesHost(host, "youtube.com")
                    || matchesHost(host, "youtu.be")
                    || matchesHost(host, "youtube-nocookie.com")) {
                return YOUTUBE;
            }
            if (matchesHost(host, "tiktok.com")) return TIKTOK;
            if (matchesHost(host, "instagram.com")) return INSTAGRAM;

            return UNKNOWN;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    /**
     * Maps a yt-dlp extractor name to a VideoPlatform.
     * yt-dlp extractor names are like "youtube", "TikTok", "Instagram", etc.
     */
    public static VideoPlatform fromExtractor(String extractor) {
        if (extractor == null || extractor.isBlank()) return UNKNOWN;
        String lower = extractor.toLowerCase();

        if (lower.contains("youtube")) return YOUTUBE;
        if (lower.contains("tiktok")) return TIKTOK;
        if (lower.contains("instagram")) return INSTAGRAM;

        return UNKNOWN;
    }

    private static String normalizeHost(String host) {
        String normalized = host.toLowerCase();
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean matchesHost(String host, String root) {
        return host.equals(root) || host.endsWith("." + root);
    }
}
