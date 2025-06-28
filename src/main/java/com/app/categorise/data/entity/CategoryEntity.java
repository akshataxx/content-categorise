package com.app.categorise.data.entity;


import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * represents the category of a piece of content
 */
@Document(collection = "category")
public class CategoryEntity {
    @MongoId
    private String id;
    @Indexed(unique = true)
    private final String name;
    private final String description;
    private String createdBy;

    public CategoryEntity(String id,String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    @PersistenceCreator
    public CategoryEntity(String id, String name, String description, String createdBy) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCreatedBy() { return createdBy; }
}
