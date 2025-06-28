package com.app.categorise.data.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Document(collection = "user")
public class UserEntity {
    @MongoId
    private String id;
    private String username;
    private String email;
    private String displayName;
}
