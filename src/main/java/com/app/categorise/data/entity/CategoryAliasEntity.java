package com.app.categorise.data.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.List;


/**
 * Represents a user's preferred alias for a specific categoryId or topic.
 * This entity links a user to a grouping key (which can be a special canonical categoryId or a generic topic)
 * and their chosen alias for it.
 */
@Document(collection = "category_aliases")
public class CategoryAliasEntity {
    @MongoId
    private String id;
    @Indexed
    private String userId;
    /** This represents the categoryId of a category (e.g., "Recipe"). */
    private String categoryId;
    /** The user-specific alias for the corresponding categoryId (e.g., "Big-Back"). */
    private String alias;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
