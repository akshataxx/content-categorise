package com.app.categorise.data.client.tiktok;

import com.app.categorise.data.dto.VideoMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.net.URI;
import java.util.List;

public final class TikTokEmbedMedia {
    private final VideoMetadata metadata;
    private final List<URI> mediaCandidates;

    public TikTokEmbedMedia(VideoMetadata metadata, List<URI> mediaCandidates) {
        this.metadata = metadata;
        this.mediaCandidates = List.copyOf(mediaCandidates);
    }

    public VideoMetadata getMetadata() {
        return metadata;
    }

    @JsonIgnore
    public List<URI> getMediaCandidates() {
        return mediaCandidates;
    }

    @Override
    public String toString() {
        return "TikTokEmbedMedia{metadata=" + metadata + ", mediaCandidateCount=" + mediaCandidates.size() + '}';
    }
}
