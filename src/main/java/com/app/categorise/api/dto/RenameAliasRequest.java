package com.app.categorise.api.dto;

import java.util.UUID;

/**
 * DTO for handling requests to rename a category alias.
 * It contains the necessary information to identify the user, the grouping to target, and the new alias name.
 */
public class RenameAliasRequest {

    private UUID userId;
    private UUID categoryId;
    private String newAlias;

    // Getters and setters
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getNewAlias() {
        return newAlias;
    }

    public void setNewAlias(String newAlias) {
        this.newAlias = newAlias;
    }
} 