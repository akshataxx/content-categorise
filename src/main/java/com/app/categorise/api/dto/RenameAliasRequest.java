package com.app.categorise.api.dto;

/**
 * DTO for handling requests to rename a category alias.
 * It contains the necessary information to identify the user, the grouping to target, and the new alias name.
 */
public class RenameAliasRequest {

    private String userId;
    private String categoryId;
    private String newAlias;

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getNewAlias() {
        return newAlias;
    }

    public void setNewAlias(String newAlias) {
        this.newAlias = newAlias;
    }
} 