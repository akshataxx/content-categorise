package com.app.categorise.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
public class User {
    private UUID id;
    private String username;
    private String email;
    private String pictureUrl;

    public User(UUID id, String username, String email, String pictureUrl) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.pictureUrl = pictureUrl;
    }

    public User(UUID id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.pictureUrl = "";
    }

    public UUID getId() {
        return id;
    }
}