package com.app.categorise.api.controller;

import com.app.categorise.api.dto.auth.AppleAuthRequest;
import com.app.categorise.api.dto.auth.GoogleAuthRequest;
import com.app.categorise.api.dto.auth.JwtAuthResponse;
import com.app.categorise.api.dto.auth.LoginRequest;
import com.app.categorise.api.dto.auth.RegisterRequest;
import com.app.categorise.domain.service.AppleAuthService;
import com.app.categorise.domain.service.AuthService;
import com.app.categorise.api.dto.auth.RefreshTokenRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;
    
    @Autowired
    private AppleAuthService appleAuthService;

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

    @GetMapping("/google")
    public ResponseEntity<?> googleAuthCallback() {
        // Handle Google OAuth redirect callback if needed
        return ResponseEntity.badRequest().body("Google authentication requires POST with ID token");
    }
    
    @PostMapping("/apple")
    public ResponseEntity<?> authenticateWithApple(@Valid @RequestBody AppleAuthRequest req) {
        System.out.println("POST /api/auth/apple received");
        System.out.println("Apple User ID: " + req.getUserIdentifier());
        System.out.println("Email: " + req.getEmail());
        try {
            JwtAuthResponse tokens = appleAuthService.authenticateWithApple(req);
            System.out.println("Apple auth successful, tokens generated");
            return ResponseEntity.ok(tokens);
        } catch (SecurityException e) {
            System.err.println("Security error during Apple authentication: " + e.getMessage());
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            System.err.println("Error during Apple authentication: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Apple authentication failed: " + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtAuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest req) throws Exception {
        JwtAuthResponse tokens = authService.refreshAccessToken(req);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            return ResponseEntity.ok(authService.register(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            return ResponseEntity.ok(authService.login(req));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid credentials");
        }
    }
}
