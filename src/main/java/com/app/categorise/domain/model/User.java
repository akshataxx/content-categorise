package com.app.categorise.domain.model;

import lombok.Data;

@Data
public class User {
    private String id;
    private String username;
    private String email;
    private String displayName;

    // Business logic methods
    public boolean isValidEmail() {
        return email != null && email.contains("@");
    }
}