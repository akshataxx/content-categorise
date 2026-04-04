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
            host = host.toLowerCase();

            if (host.contains("youtube.com") || host.contains("youtu.be")) return YOUTUBE;
            if (host.contains("tiktok.com")) return TIKTOK;
            if (host.contains("instagram.com")) return INSTAGRAM;
            if (host.contains("vimeo.com")) return VIMEO;
            if (host.contains("twitter.com") || host.contains("x.com")) return TWITTER;
            if (host.contains("facebook.com") || host.contains("fb.watch")) return FACEBOOK;
            if (host.contains("reddit.com")) return REDDIT;
            if (host.contains("twitch.tv")) return TWITCH;
            if (host.contains("dailymotion.com")) return DAILYMOTION;

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
        if (lower.contains("vimeo")) return VIMEO;
        if (lower.contains("twitter") || lower.contains("x.com")) return TWITTER;
        if (lower.contains("facebook") || lower.contains("fb")) return FACEBOOK;
        if (lower.contains("reddit")) return REDDIT;
        if (lower.contains("twitch")) return TWITCH;
        if (lower.contains("dailymotion")) return DAILYMOTION;

        return UNKNOWN;
    }
}
