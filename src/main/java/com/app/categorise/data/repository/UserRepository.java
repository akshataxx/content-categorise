package com.app.categorise.data.repository;

import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private String id;
    private String username;
    private String email;
    private String displayName;

    // Business methods
    public boolean isValidEmail() {
        return email != null && email.contains("@");
    }
}