package com.app.categorise.data.entity;


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

    @Indexed
    private String name;
}
