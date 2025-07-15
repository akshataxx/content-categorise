package com.app.categorise.api.dto.auth;

import jakarta.validation.constraints.NotEmpty;

public class GoogleAuthRequest {

    @NotEmpty
    private String idToken;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
} 