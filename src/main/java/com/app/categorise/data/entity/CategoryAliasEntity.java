package com.app.categorise.data.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.index.Indexed;


/**
 * Represents an alias for a category used by a user.
 * This class is used to store user-specific aliases for canonical categories.
 */
@Document(collection = "category_alias")
public class CategoryAliasEntity {
    @MongoId
    private String id;
    @Indexed
    private String userId;

    @Indexed
    private String canonicalCategory; //only store canonical category names used in the db

    private String alias; //store aliases displayed on the ui

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCanonicalCategory() { return canonicalCategory; }
    public void setCanonicalCategory(String canonicalCategory) { this.canonicalCategory = canonicalCategory; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
