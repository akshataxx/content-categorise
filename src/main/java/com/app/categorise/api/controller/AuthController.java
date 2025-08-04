package com.app.categorise.api.controller;

import com.app.categorise.api.dto.auth.GoogleAuthRequest;
import com.app.categorise.api.dto.auth.JwtAuthResponse;
import com.app.categorise.domain.service.AuthService;
import com.app.categorise.api.dto.auth.RefreshTokenRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody GoogleAuthRequest req) {
        System.out.println("POST /api/auth/google received");
        try {
            JwtAuthResponse tokens = authService.authenticateWithGoogle(req);
            System.out.println("Tokens: " + tokens);
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            System.err.println("Error during authentication: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtAuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest req) throws Exception {
        JwtAuthResponse tokens = authService.refreshAccessToken(req);
        return ResponseEntity.ok(tokens);
    }
}
