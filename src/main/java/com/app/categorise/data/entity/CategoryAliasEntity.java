package com.app.categorise.data.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.index.Indexed;


/**
 * Represents a user's preferred alias for a specific category or topic.
 * This entity links a user to a grouping key (which can be a special canonical category or a generic topic)
 * and their chosen alias for it.
 */
@Document(collection = "category_aliases")
public class CategoryAliasEntity {
    @MongoId
    private String id;
    @Indexed
    private String userId;
    /** The key used for grouping. This can be a canonical category (e.g., "Recipe") or a generic topic (e.g., "tech"). */
    private String groupingKey;
    /** The user-specific alias for the corresponding grouping key (e.g., "Big-Back"). */
    private String alias;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGroupingKey() { return groupingKey; }
    public void setGroupingKey(String groupingKey) { this.groupingKey = groupingKey; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
