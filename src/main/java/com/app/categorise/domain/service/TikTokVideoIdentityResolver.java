package com.app.categorise.domain.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TikTokVideoIdentityResolver {
    private static final Set<String> DIRECT_HOSTS = Set.of("www.tiktok.com", "m.tiktok.com");
    private static final Set<String> SHORT_LINK_HOSTS = Set.of("vm.tiktok.com", "vt.tiktok.com");
    private static final Pattern DIRECT_VIDEO_PATH = Pattern.compile("^/@[^/]+/video/([1-9][0-9]{14,21})/?$");
    private static final Pattern EMBED_PATH = Pattern.compile("^/embed/v2/([1-9][0-9]{14,21})/?$");
    private static final Pattern PLAYER_PATH = Pattern.compile("^/player/v1/([1-9][0-9]{14,21})/?$");
    private static final int MAX_REDIRECT_HOPS = 3;

    private final RedirectResolver redirectResolver;

    public TikTokVideoIdentityResolver() {
        this(new HttpRedirectResolver());
    }

    TikTokVideoIdentityResolver(RedirectResolver redirectResolver) {
        this.redirectResolver = redirectResolver;
    }

    public String resolve(String value) {
        URI uri = parseAndValidate(value);
        if (SHORT_LINK_HOSTS.contains(uri.getHost())) {
            uri = resolveShortLink(uri);
        }
        return extractVideoId(uri);
    }

    private URI resolveShortLink(URI uri) {
        URI current = uri;
        for (int hop = 0; hop < MAX_REDIRECT_HOPS; hop++) {
            URI next = redirectResolver.redirect(current);
            if (next == null) {
                throw invalidUrl();
            }
            current = parseAndValidate(next.toString());
            if (DIRECT_HOSTS.contains(current.getHost())) {
                return current;
            }
            if (!SHORT_LINK_HOSTS.contains(current.getHost())) {
                throw invalidUrl();
            }
        }
        throw invalidUrl();
    }

    private static URI parseAndValidate(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null
                || (uri.getPort() != -1 && uri.getPort() != 443) || isIpLiteral(uri.getHost())
                || !(DIRECT_HOSTS.contains(uri.getHost()) || SHORT_LINK_HOSTS.contains(uri.getHost()))) {
                throw invalidUrl();
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw invalidUrl();
        }
    }

    private static String extractVideoId(URI uri) {
        if (!DIRECT_HOSTS.contains(uri.getHost())) {
            throw invalidUrl();
        }
        for (Pattern pattern : new Pattern[] { DIRECT_VIDEO_PATH, EMBED_PATH, PLAYER_PATH }) {
            Matcher matcher = pattern.matcher(uri.getPath());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        throw invalidUrl();
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("^[0-9.]+$");
    }

    private static IllegalArgumentException invalidUrl() {
        return new IllegalArgumentException("Unsupported TikTok video URL");
    }

    @FunctionalInterface
    interface RedirectResolver {
        URI redirect(URI uri);
    }

    private static final class HttpRedirectResolver implements RedirectResolver {
        private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        @Override
        public URI redirect(URI uri) {
            try {
                HttpResponse<Void> response = client.send(HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 300 || response.statusCode() >= 400) {
                    throw invalidUrl();
                }
                return uri.resolve(response.headers().firstValue("Location").orElseThrow(TikTokVideoIdentityResolver::invalidUrl));
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw invalidUrl();
            }
        }
    }
}
