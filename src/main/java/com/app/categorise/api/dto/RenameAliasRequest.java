package com.app.categorise.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for handling requests to rename a category alias.
 * It contains the necessary information to identify the user, the grouping to target, and the new alias name.
 */
public class RenameAliasRequest {
    @NotNull
    private UUID userId;

    @NotNull
    private UUID categoryId;

    @NotBlank
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
