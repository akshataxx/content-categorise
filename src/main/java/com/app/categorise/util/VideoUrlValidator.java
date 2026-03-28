package com.app.categorise.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

/**
 * Validates video URLs to prevent SSRF, command injection, and other URL-based attacks.
 * Enforces HTTPS-only, domain allowlisting, and blocks internal/metadata IP ranges.
 */
public final class VideoUrlValidator {

    private VideoUrlValidator() {}

    public static final int MAX_URL_LENGTH = 2048;

    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "youtu.be",
            "tiktok.com",
            "www.tiktok.com",
            "vm.tiktok.com",
            "instagram.com",
            "www.instagram.com",
            "vimeo.com",
            "www.vimeo.com",
            "twitter.com",
            "www.twitter.com",
            "x.com",
            "www.x.com",
            "facebook.com",
            "www.facebook.com",
            "fb.watch",
            "dailymotion.com",
            "www.dailymotion.com",
            "twitch.tv",
            "www.twitch.tv",
            "clips.twitch.tv",
            "reddit.com",
            "www.reddit.com",
            "v.redd.it"
    );

    /**
     * Validates a video URL for safety and correctness.
     *
     * @param url the URL to validate
     * @throws IllegalArgumentException if the URL is invalid, uses a disallowed scheme,
     *                                  targets a non-allowlisted domain, or resolves to an internal IP
     */
    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Missing 'videoUrl' in request body");
        }

        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL exceeds maximum length of " + MAX_URL_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format");
        }

        // Enforce scheme
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Only HTTPS URLs are allowed");
        }

        // Enforce host is present
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must contain a valid hostname");
        }

        // Check domain allowlist
        String lowerHost = host.toLowerCase();
        if (!isAllowedDomain(lowerHost)) {
            throw new IllegalArgumentException("Domain '" + host + "' is not a supported video platform");
        }

        // Block internal/metadata IP ranges
        blockInternalAddresses(host);
    }

    /**
     * Checks if the host matches an allowed domain (exact match or subdomain of an allowed domain).
     */
    private static boolean isAllowedDomain(String host) {
        if (ALLOWED_DOMAINS.contains(host)) {
            return true;
        }
        // Check if host is a subdomain of any allowed domain (e.g., "m.tiktok.com")
        for (String allowed : ALLOWED_DOMAINS) {
            if (host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Blocks URLs that resolve to internal, link-local, loopback, or cloud metadata IP addresses.
     */
    private static void blockInternalAddresses(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isAnyLocalAddress()
                        || isCloudMetadataAddress(addr)) {
                    throw new IllegalArgumentException("URL must not target internal network addresses");
                }
            }
        } catch (IllegalArgumentException e) {
            // Re-throw our own exceptions
            throw e;
        } catch (Exception e) {
            // DNS resolution failure — reject to be safe
            throw new IllegalArgumentException("Unable to resolve URL hostname");
        }
    }

    /**
     * Checks for well-known cloud metadata service IPs (AWS 169.254.169.254, GCP, Azure, etc.)
     */
    private static boolean isCloudMetadataAddress(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length == 4) {
            // AWS/GCP metadata: 169.254.169.254
            if ((bytes[0] & 0xFF) == 169 && (bytes[1] & 0xFF) == 254
                    && (bytes[2] & 0xFF) == 169 && (bytes[3] & 0xFF) == 254) {
                return true;
            }
        }
        return false;
    }
}
