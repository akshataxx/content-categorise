package com.app.categorise.domain.model;

import java.util.UUID;

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
}