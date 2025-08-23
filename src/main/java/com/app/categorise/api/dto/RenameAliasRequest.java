package com.app.categorise.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for handling requests to rename a category alias.
 * It contains the necessary information to identify the grouping to target and the new alias name.
 * The user is identified through authentication.
 */
public class RenameAliasRequest {
    @NotNull
    private UUID categoryId;

    @NotBlank
    private String newAlias;

    // Getters and setters
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
