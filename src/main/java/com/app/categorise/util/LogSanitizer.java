package com.app.categorise.util;

/**
 * Sanitizes user-controlled input before logging to prevent log injection attacks.
 * Strips newlines, carriage returns, and other control characters that could be used
 * to forge log entries.
 */
public final class LogSanitizer {

    private LogSanitizer() {}

    /**
     * Sanitizes a string for safe inclusion in log messages.
     * Replaces newlines, carriage returns, tabs, and other control characters with underscores.
     *
     * @param input the user-controlled input to sanitize
     * @return sanitized string safe for logging, or "[null]" if input is null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "[null]";
        }
        // Replace control characters (ASCII 0-31 and 127) with underscores
        return input.replaceAll("[\\p{Cntrl}]", "_");
    }
}
