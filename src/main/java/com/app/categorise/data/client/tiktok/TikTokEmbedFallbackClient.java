package com.app.categorise.data.client.tiktok;

import com.app.categorise.data.dto.VideoMetadata;
import com.app.categorise.config.TikTokIngestionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
public class TikTokEmbedFallbackClient {
    private static final int MAX_REDIRECTS = 3;
    private final HttpClient httpClient;
    private final TikTokEmbedPageParser parser;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final long maxHtmlBytes;
    private final long maxMediaBytes;
    private final HostResolver hostResolver;

    public TikTokEmbedFallbackClient() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), new TikTokEmbedPageParser(), 10, 30, 2_097_152, 157_286_400, InetAddress::getAllByName);
    }

    @Autowired
    public TikTokEmbedFallbackClient(TikTokIngestionProperties properties) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getEmbedFallback().getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
            new TikTokEmbedPageParser(),
            properties.getEmbedFallback().getConnectTimeoutSeconds(),
            properties.getEmbedFallback().getReadTimeoutSeconds(),
            properties.getEmbedFallback().getMaxHtmlBytes(),
            properties.getEmbedFallback().getMaxMediaBytes(), InetAddress::getAllByName);
    }

    public TikTokEmbedFallbackClient(HttpClient httpClient, TikTokEmbedPageParser parser, int connectTimeoutSeconds,
                                     int readTimeoutSeconds, long maxHtmlBytes, long maxMediaBytes) {
        this(httpClient, parser, connectTimeoutSeconds, readTimeoutSeconds, maxHtmlBytes, maxMediaBytes, InetAddress::getAllByName);
    }

    TikTokEmbedFallbackClient(HttpClient httpClient, TikTokEmbedPageParser parser, int connectTimeoutSeconds,
                              int readTimeoutSeconds, long maxHtmlBytes, long maxMediaBytes, HostResolver hostResolver) {
        this.httpClient = httpClient;
        this.parser = parser;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.maxHtmlBytes = maxHtmlBytes;
        this.maxMediaBytes = maxMediaBytes;
        this.hostResolver = hostResolver;
    }

    public DownloadedMedia download(String videoId, Path temporaryDirectory) {
        try {
            TikTokEmbedMedia embed = fetch(videoId);
            try {
                return new DownloadedMedia(embed.getMetadata(), download(embed, temporaryDirectory));
            } catch (SignedUrlUnavailableException firstFailure) {
                TikTokEmbedMedia refreshed = fetch(videoId);
                return new DownloadedMedia(refreshed.getMetadata(), download(refreshed, temporaryDirectory));
            }
        } catch (TikTokEmbedFallbackException e) {
            throw e;
        } catch (Exception e) {
            throw new TikTokEmbedFallbackException("request-failed");
        }
    }

    public TikTokEmbedMedia fetch(String videoId) {
        try {
            return fetchEmbed(videoId);
        } catch (TikTokEmbedFallbackException e) {
            throw e;
        } catch (Exception e) {
            throw new TikTokEmbedFallbackException("request-failed");
        }
    }

    public Path download(TikTokEmbedMedia embed, Path temporaryDirectory) {
        try {
            return downloadCandidate(embed.getMediaCandidates(), temporaryDirectory);
        } catch (TikTokEmbedFallbackException e) {
            throw e;
        } catch (Exception e) {
            throw new TikTokEmbedFallbackException("request-failed");
        }
    }

    private TikTokEmbedMedia fetchEmbed(String videoId) throws IOException, InterruptedException {
        URI uri = URI.create("https://www.tiktok.com/embed/v2/" + videoId);
        HttpResponse<InputStream> response = sendFollowingEmbedRedirects(uri, videoId);
        if (response.statusCode() / 100 != 2 || !contentTypeStartsWith(response, "text/html")) {
            close(response.body());
            throw new TikTokEmbedFallbackException("invalid-embed-response");
        }
        try (InputStream body = response.body()) {
            return parser.parse(new String(readLimited(body, maxHtmlBytes)), videoId);
        }
    }

    private Path downloadCandidate(List<URI> candidates, Path directory) throws IOException, InterruptedException {
        for (URI candidate : candidates) {
            validateCdnUri(candidate);
            Path downloaded = null;
            try {
                downloaded = Files.createTempFile(directory, "tiktok-media-", ".mp4");
                HttpResponse<InputStream> response = sendFollowingCdnRedirects(candidate);
                int status = response.statusCode();
                if (status == 401 || status == 403 || status == 404 || status == 410) {
                    close(response.body());
                    Files.deleteIfExists(downloaded);
                    throw new SignedUrlUnavailableException();
                }
                if (status / 100 != 2 || !isMediaContentType(response)) {
                    close(response.body());
                    Files.deleteIfExists(downloaded);
                    continue;
                }
                long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declaredLength > maxMediaBytes) {
                    close(response.body());
                    Files.deleteIfExists(downloaded);
                    throw new TikTokEmbedFallbackException("media-too-large");
                }
                try (InputStream body = response.body(); var output = Files.newOutputStream(downloaded)) {
                    output.write(readLimited(body, maxMediaBytes));
                }
                return downloaded;
            } catch (TikTokEmbedFallbackException | SignedUrlUnavailableException e) {
                if (downloaded != null) Files.deleteIfExists(downloaded);
                throw e;
            } catch (IOException e) {
                if (downloaded != null) Files.deleteIfExists(downloaded);
            }
        }
        throw new TikTokEmbedFallbackException("media-unavailable");
    }

    private HttpResponse<InputStream> sendFollowingEmbedRedirects(URI uri, String videoId) throws IOException, InterruptedException {
        URI current = uri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpResponse<InputStream> response = send(current, false);
            if (!isRedirect(response.statusCode())) return response;
            close(response.body());
            current = redirectTarget(response, current, true);
            if (!current.getHost().equals("www.tiktok.com") || !current.getPath().equals("/embed/v2/" + videoId)) {
                throw new TikTokEmbedFallbackException("invalid-embed-redirect");
            }
        }
        throw new TikTokEmbedFallbackException("too-many-redirects");
    }

    private HttpResponse<InputStream> sendFollowingCdnRedirects(URI uri) throws IOException, InterruptedException {
        URI current = uri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpResponse<InputStream> response = send(current, true);
            if (!isRedirect(response.statusCode())) return response;
            close(response.body());
            current = redirectTarget(response, current, false);
            validateCdnUri(current);
        }
        throw new TikTokEmbedFallbackException("too-many-redirects");
    }

    private HttpResponse<InputStream> send(URI uri, boolean media) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(readTimeoutSeconds));
        if (media) builder.header("Referer", "https://www.tiktok.com/");
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private static URI redirectTarget(HttpResponse<?> response, URI current, boolean embed) {
        String location = response.headers().firstValue("Location").orElseThrow(() -> new TikTokEmbedFallbackException(embed ? "invalid-embed-redirect" : "invalid-media-redirect"));
        try {
            return current.resolve(URI.create(location));
        } catch (IllegalArgumentException e) {
            throw new TikTokEmbedFallbackException(embed ? "invalid-embed-redirect" : "invalid-media-redirect");
        }
    }

    private void validateCdnUri(URI uri) {
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
            || (uri.getPort() != -1 && uri.getPort() != 443) || host == null
            || !(host.equals("tiktokcdn.com") || host.endsWith(".tiktokcdn.com")) || isIpLiteral(host)) {
            throw new TikTokEmbedFallbackException("invalid-media-host");
        }
        try {
            for (InetAddress address : hostResolver.resolve(host)) {
                if (isPrivateOrReserved(address)) {
                    throw new TikTokEmbedFallbackException("invalid-media-host");
                }
            }
        } catch (IOException e) {
            throw new TikTokEmbedFallbackException("invalid-media-host");
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}");
    }

    private static boolean isPrivateOrReserved(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()
            || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc);
    }

    private static boolean isRedirect(int status) { return status >= 300 && status < 400; }
    private static boolean contentTypeStartsWith(HttpResponse<?> response, String expected) {
        return response.headers().firstValue("Content-Type").map(value -> value.toLowerCase().startsWith(expected)).orElse(false);
    }
    private static boolean isMediaContentType(HttpResponse<?> response) {
        return contentTypeStartsWith(response, "video/") || contentTypeStartsWith(response, "audio/") || contentTypeStartsWith(response, "application/octet-stream");
    }
    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        try (var output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) {
                if (output.size() + read > limit) throw new TikTokEmbedFallbackException("response-too-large");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
    private static void close(InputStream input) { try { input.close(); } catch (IOException ignored) { } }

    public record DownloadedMedia(VideoMetadata metadata, Path mediaFile) {
        @Override public String toString() { return "DownloadedMedia{metadata=" + metadata + '}'; }
    }
    private static final class SignedUrlUnavailableException extends RuntimeException { }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws IOException;
    }
}
