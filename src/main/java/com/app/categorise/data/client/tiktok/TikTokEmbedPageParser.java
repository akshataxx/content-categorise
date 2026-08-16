package com.app.categorise.data.client.tiktok;

import com.app.categorise.data.dto.VideoMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TikTokEmbedPageParser {
    private static final Pattern FRONTITY_SCRIPT = Pattern.compile(
        "<script\\b[^>]*\\bid\\s*=\\s*([\"'])__FRONTITY_CONNECT_STATE__\\1[^>]*>(.*?)</script\\s*>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public TikTokEmbedPageParser() {
        this(new ObjectMapper());
    }

    TikTokEmbedPageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TikTokEmbedMedia parse(String html, String expectedVideoId) {
        try {
            JsonNode videoData = videoData(html, expectedVideoId);
            JsonNode itemInfos = requiredObject(videoData, "itemInfos");
            JsonNode authorInfos = requiredObject(videoData, "authorInfos");
            String id = requiredText(itemInfos, "id");
            if (!expectedVideoId.equals(id)) {
                throw invalidEmbed();
            }

            VideoMetadata metadata = new VideoMetadata();
            String description = requiredText(itemInfos, "text");
            metadata.setId(id);
            metadata.setDescription(description);
            metadata.setTitle(truncateCodePoints(description, 72));
            metadata.setUploadedEpoch(requiredLong(itemInfos, "createTime"));
            metadata.setDuration(requiredInt(requiredObject(requiredObject(itemInfos, "video"), "videoMeta"), "duration"));
            metadata.setAccountId(requiredText(authorInfos, "userId"));
            metadata.setAccount(requiredText(authorInfos, "uniqueId"));
            metadata.setIdentifierId(requiredText(authorInfos, "secUid"));
            metadata.setIdentifier(requiredText(authorInfos, "nickName"));
            metadata.setExtractor("TikTok");

            JsonNode urls = requiredObject(itemInfos, "video").path("urls");
            if (!urls.isArray() || urls.isEmpty()) {
                throw invalidEmbed();
            }
            List<URI> candidates = new ArrayList<>();
            for (JsonNode url : urls) {
                candidates.add(approvedMediaUri(requiredText(url)));
            }
            return new TikTokEmbedMedia(metadata, candidates);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw invalidEmbed();
        }
    }

    private JsonNode videoData(String html, String expectedVideoId) throws Exception {
        Matcher matcher = FRONTITY_SCRIPT.matcher(html);
        if (!matcher.find()) {
            throw invalidEmbed();
        }
        JsonNode root = objectMapper.readTree(matcher.group(2));
        return requiredObject(requiredObject(requiredObject(root, "source"), "data"), "/embed/v2/" + expectedVideoId)
            .path("videoData");
    }

    private static JsonNode requiredObject(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isObject()) {
            throw invalidEmbed();
        }
        return node;
    }

    private static String requiredText(JsonNode parent, String field) {
        return requiredText(parent.path(field));
    }

    private static String requiredText(JsonNode node) {
        if (!node.isTextual() || node.asText().isBlank()) {
            throw invalidEmbed();
        }
        return node.asText();
    }

    private static int requiredInt(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() <= 0) {
            throw invalidEmbed();
        }
        return node.asInt();
    }

    private static long requiredLong(JsonNode parent, String field) {
        try {
            long value = Long.parseLong(requiredText(parent, field));
            if (value <= 0) {
                throw invalidEmbed();
            }
            return value;
        } catch (NumberFormatException e) {
            throw invalidEmbed();
        }
    }

    private static URI approvedMediaUri(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || host == null
                || !(host.equals("tiktokcdn.com") || host.endsWith(".tiktokcdn.com"))) {
                throw invalidEmbed();
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw invalidEmbed();
        }
    }

    private static String truncateCodePoints(String value, int limit) {
        if (value.codePointCount(0, value.length()) <= limit) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, limit));
    }

    private static IllegalArgumentException invalidEmbed() {
        return new IllegalArgumentException("Invalid TikTok embed response");
    }
}
