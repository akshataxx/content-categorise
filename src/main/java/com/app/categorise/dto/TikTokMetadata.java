package com.app.categorise.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TikTokMetadata {
    @JsonProperty("description")
    private String description;
    // other fields if needed

    public String getDescription() { return description; }
}
