package com.app.categorise.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class TikTokMetadata {
    private String description;
    @JsonProperty("fulltitle")
    private String title;
    private int duration;
    @JsonProperty("timestamp")
    private long uploadedEpoch;
    // yt-dlp calls the account (the alias name) the uploader
    @JsonProperty("uploader_id")
    private String accountId;
    @JsonProperty("uploader")
    private String account;
    @JsonProperty("channel_id")
    private String identifierId;
    @JsonProperty("channel")
    private String identifier;

    public String getDescription() { return description; }
    public String getTitle() { return title; }
    public int getDuration() { return duration; }
    public long getUploadedEpoch() { return uploadedEpoch; }
    public String getAccountId() { return accountId; }
    public String getAccount() { return account; }
    public String getIdentifierId() { return identifierId; }
    public String getIdentifier() { return identifier; }

}
