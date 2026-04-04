package com.app.categorise.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoPlatformTest {

    @Nested
    @DisplayName("fromUrl")
    class FromUrl {

        @Test
        @DisplayName("detects YouTube from youtube.com")
        void detectsYoutube() {
            assertThat(VideoPlatform.fromUrl("https://www.youtube.com/watch?v=abc123")).isEqualTo(VideoPlatform.YOUTUBE);
        }

        @Test
        @DisplayName("detects YouTube from youtu.be")
        void detectsYoutubeShortUrl() {
            assertThat(VideoPlatform.fromUrl("https://youtu.be/abc123")).isEqualTo(VideoPlatform.YOUTUBE);
        }

        @Test
        @DisplayName("detects YouTube Shorts")
        void detectsYoutubeShorts() {
            assertThat(VideoPlatform.fromUrl("https://www.youtube.com/shorts/abc123")).isEqualTo(VideoPlatform.YOUTUBE);
        }

        @Test
        @DisplayName("detects TikTok")
        void detectsTikTok() {
            assertThat(VideoPlatform.fromUrl("https://www.tiktok.com/@user/video/123")).isEqualTo(VideoPlatform.TIKTOK);
        }

        @Test
        @DisplayName("detects TikTok vm short link")
        void detectsTikTokVm() {
            assertThat(VideoPlatform.fromUrl("https://vm.tiktok.com/abc123")).isEqualTo(VideoPlatform.TIKTOK);
        }

        @Test
        @DisplayName("detects Instagram")
        void detectsInstagram() {
            assertThat(VideoPlatform.fromUrl("https://www.instagram.com/reel/abc123/")).isEqualTo(VideoPlatform.INSTAGRAM);
        }

        @Test
        @DisplayName("detects Vimeo")
        void detectsVimeo() {
            assertThat(VideoPlatform.fromUrl("https://vimeo.com/123456")).isEqualTo(VideoPlatform.VIMEO);
        }

        @Test
        @DisplayName("detects Twitter/X")
        void detectsTwitter() {
            assertThat(VideoPlatform.fromUrl("https://x.com/user/status/123")).isEqualTo(VideoPlatform.TWITTER);
            assertThat(VideoPlatform.fromUrl("https://twitter.com/user/status/123")).isEqualTo(VideoPlatform.TWITTER);
        }

        @Test
        @DisplayName("detects Facebook")
        void detectsFacebook() {
            assertThat(VideoPlatform.fromUrl("https://www.facebook.com/watch?v=123")).isEqualTo(VideoPlatform.FACEBOOK);
        }

        @Test
        @DisplayName("detects Reddit")
        void detectsReddit() {
            assertThat(VideoPlatform.fromUrl("https://www.reddit.com/r/sub/comments/abc/title/")).isEqualTo(VideoPlatform.REDDIT);
        }

        @Test
        @DisplayName("returns UNKNOWN for unrecognized URLs")
        void returnsUnknownForUnrecognized() {
            assertThat(VideoPlatform.fromUrl("https://example.com/video")).isEqualTo(VideoPlatform.UNKNOWN);
        }

        @Test
        @DisplayName("returns UNKNOWN for null URL")
        void returnsUnknownForNull() {
            assertThat(VideoPlatform.fromUrl(null)).isEqualTo(VideoPlatform.UNKNOWN);
        }

        @Test
        @DisplayName("returns UNKNOWN for malformed URL")
        void returnsUnknownForMalformed() {
            assertThat(VideoPlatform.fromUrl("not-a-url")).isEqualTo(VideoPlatform.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("fromExtractor")
    class FromExtractor {

        @Test
        @DisplayName("maps yt-dlp extractor names to platform")
        void mapsExtractorNames() {
            assertThat(VideoPlatform.fromExtractor("youtube")).isEqualTo(VideoPlatform.YOUTUBE);
            assertThat(VideoPlatform.fromExtractor("TikTok")).isEqualTo(VideoPlatform.TIKTOK);
            assertThat(VideoPlatform.fromExtractor("Instagram")).isEqualTo(VideoPlatform.INSTAGRAM);
            assertThat(VideoPlatform.fromExtractor("vimeo")).isEqualTo(VideoPlatform.VIMEO);
        }

        @Test
        @DisplayName("is case-insensitive")
        void isCaseInsensitive() {
            assertThat(VideoPlatform.fromExtractor("YOUTUBE")).isEqualTo(VideoPlatform.YOUTUBE);
            assertThat(VideoPlatform.fromExtractor("tiktok")).isEqualTo(VideoPlatform.TIKTOK);
        }

        @Test
        @DisplayName("returns UNKNOWN for null or unrecognized extractor")
        void returnsUnknownForNull() {
            assertThat(VideoPlatform.fromExtractor(null)).isEqualTo(VideoPlatform.UNKNOWN);
            assertThat(VideoPlatform.fromExtractor("SomeNewPlatform")).isEqualTo(VideoPlatform.UNKNOWN);
        }
    }
}
