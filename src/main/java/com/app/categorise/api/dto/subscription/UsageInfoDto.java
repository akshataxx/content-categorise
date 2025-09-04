package com.app.categorise.api.dto.subscription;

/**
 * DTO for user usage information
 */
public class UsageInfoDto {
    private boolean isPremium;
    private int remainingFreeTranscriptions;
    
    public UsageInfoDto() {}
    
    public UsageInfoDto(boolean isPremium, int remainingFreeTranscriptions) {
        this.isPremium = isPremium;
        this.remainingFreeTranscriptions = remainingFreeTranscriptions;
    }
    
    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }
    
    public int getRemainingFreeTranscriptions() { return remainingFreeTranscriptions; }
    public void setRemainingFreeTranscriptions(int remainingFreeTranscriptions) { 
        this.remainingFreeTranscriptions = remainingFreeTranscriptions; 
    }
}