package com.app.categorise.domain.model;

import lombok.Data;

@Data
public class CategoryAlias {
    private String id;
    private String userId;
    private String canonicalCategory;
    private String alias;

    // Business methods
    public boolean isValid() {
        return canonicalCategory != null && !canonicalCategory.isEmpty()
                && alias != null && !alias.isEmpty();
    }

    public boolean isForUser(String userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public boolean matchesCanonicalCategory(String category) {
        return canonicalCategory != null && canonicalCategory.equalsIgnoreCase(category);
    }
}
