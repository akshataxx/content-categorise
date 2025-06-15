package com.app.categorise.models.entity;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

/**
 * represents the category of a piece of content
 */
@Document(collection = "category")
public class Category {

    @MongoId
    private String id;

    @Indexed
    private String name;
}
