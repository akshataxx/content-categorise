package com.app.categorise.api.controller;

import com.app.categorise.api.dto.auth.GoogleAuthRequest;
import com.app.categorise.api.dto.auth.JwtAuthResponse;
import com.app.categorise.domain.service.AuthService;
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
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody GoogleAuthRequest googleAuthRequest) {
        System.out.println("POST /api/auth/google received");
        try {
            String jwt = authService.authenticateWithGoogle(googleAuthRequest);
            System.out.println("JWT: " + jwt);
            return ResponseEntity.ok(new JwtAuthResponse(jwt));
        } catch (Exception e) {
            System.err.println("Error during authentication: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
