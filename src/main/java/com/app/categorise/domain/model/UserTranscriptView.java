package com.app.categorise.domain.model;

/**
 * UserTranscriptView - Combined view for API responses
 * Combines base transcript data with user-specific information for API responses
 */
public class UserTranscriptView {
    private BaseTranscript baseTranscript;
    private UserTranscript userTranscript;
    private String categoryName;
    private String categoryAlias;

    public UserTranscriptView() {}

    public UserTranscriptView(BaseTranscript baseTranscript, UserTranscript userTranscript, 
                             String categoryName, String categoryAlias) {
        this.baseTranscript = baseTranscript;
        this.userTranscript = userTranscript;
        this.categoryName = categoryName;
        this.categoryAlias = categoryAlias;
    }

    // Getters and Setters
    public BaseTranscript getBaseTranscript() {
        return baseTranscript;
    }

    public void setBaseTranscript(BaseTranscript baseTranscript) {
        this.baseTranscript = baseTranscript;
    }

    public UserTranscript getUserTranscript() {
        return userTranscript;
    }

    public void setUserTranscript(UserTranscript userTranscript) {
        this.userTranscript = userTranscript;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryAlias() {
        return categoryAlias;
    }

    public void setCategoryAlias(String categoryAlias) {
        this.categoryAlias = categoryAlias;
    }

    @Override
    public String toString() {
        return "UserTranscriptView{" +
                "baseTranscript=" + baseTranscript +
                ", userTranscript=" + userTranscript +
                ", categoryName='" + categoryName + '\'' +
                ", categoryAlias='" + categoryAlias + '\'' +
                '}';
    }
}