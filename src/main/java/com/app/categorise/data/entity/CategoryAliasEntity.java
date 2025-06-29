package com.app.categorise.data.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.Data;

import java.util.UUID;

/**
 * Represents a user's preferred alias for a specific categoryId or topic.
 * This entity links a user to a grouping key (which can be a special canonical categoryId or a generic topic)
 * and their chosen alias for it.
 */
@Entity
@Data
@Table(name = "category_aliases")
public class CategoryAliasEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    /** This represents the categoryId of a category (e.g., "Recipe"). */
    private UUID categoryId;
    /** The user-specific alias for the corresponding categoryId (e.g., "Big-Back"). */
    private String alias;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public UUID getId() { return id; }
}
