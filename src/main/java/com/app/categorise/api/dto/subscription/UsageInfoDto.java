package com.app.categorise.api.dto.subscription;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for user usage information
 */
public class UsageInfoDto {
    @JsonProperty("isPremium")
    private boolean isPremium;
    
    @JsonProperty("remainingFreeTranscriptions")
    private int remainingFreeTranscriptions;
    
    public UsageInfoDto() {}
    
    public UsageInfoDto(boolean isPremium, int remainingFreeTranscriptions) {
        this.isPremium = isPremium;
        this.remainingFreeTranscriptions = remainingFreeTranscriptions;
    }
    
    @JsonProperty("isPremium")
    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }
    
    @JsonProperty("remainingFreeTranscriptions")
    public int getRemainingFreeTranscriptions() { return remainingFreeTranscriptions; }
    public void setRemainingFreeTranscriptions(int remainingFreeTranscriptions) { 
        this.remainingFreeTranscriptions = remainingFreeTranscriptions; 
    }
}