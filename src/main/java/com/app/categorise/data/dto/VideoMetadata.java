package com.app.categorise.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoMetadata {
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
    private String extractor;

    public String getDescription() { return description; }
    public String getTitle() { return title; }
    public int getDuration() { return duration; }
    public long getUploadedEpoch() { return uploadedEpoch; }
    public String getAccountId() { return accountId; }
    public String getAccount() { return account; }
    public String getIdentifierId() { return identifierId; }
    public String getIdentifier() { return identifier; }
    public String getExtractor() { return extractor; }

    public void setDescription(String description) { this.description = description; }
    public void setTitle(String title) { this.title = title; }
    public void setDuration(int duration) { this.duration = duration; }
    public void setUploadedEpoch(long uploadedEpoch) { this.uploadedEpoch = uploadedEpoch; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setAccount(String account) { this.account = account; }
    public void setIdentifierId(String identifierId) { this.identifierId = identifierId; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public void setExtractor(String extractor) { this.extractor = extractor; }
}
