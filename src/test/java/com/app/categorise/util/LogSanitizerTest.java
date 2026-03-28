package com.app.categorise.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogSanitizerTest {

    @Nested
    @DisplayName("sanitize")
    class Sanitize {

        @Test
        @DisplayName("Returns normal strings unchanged")
        void returnsNormalStringsUnchanged() {
            assertEquals("https://youtube.com/watch?v=abc", LogSanitizer.sanitize("https://youtube.com/watch?v=abc"));
            assertEquals("simple text", LogSanitizer.sanitize("simple text"));
        }

        @Test
        @DisplayName("Replaces newlines and carriage returns")
        void replacesNewlinesAndCarriageReturns() {
            String malicious = "https://evil.com\n2026-03-28 INFO - User admin logged in successfully";
            String sanitized = LogSanitizer.sanitize(malicious);
            assertFalse(sanitized.contains("\n"));
            assertFalse(sanitized.contains("\r"));
            assertTrue(sanitized.contains("_"));
        }

        @Test
        @DisplayName("Replaces tabs and other control characters")
        void replacesControlCharacters() {
            String withTab = "before\tafter";
            String sanitized = LogSanitizer.sanitize(withTab);
            assertFalse(sanitized.contains("\t"));

            String withNull = "before\0after";
            String sanitizedNull = LogSanitizer.sanitize(withNull);
            assertFalse(sanitizedNull.contains("\0"));
        }

        @Test
        @DisplayName("Handles null input")
        void handlesNullInput() {
            assertEquals("[null]", LogSanitizer.sanitize(null));
        }

        @Test
        @DisplayName("Handles empty string")
        void handlesEmptyString() {
            assertEquals("", LogSanitizer.sanitize(""));
        }
    }
}
