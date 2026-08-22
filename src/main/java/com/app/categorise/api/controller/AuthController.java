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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;
    
    @Autowired
    private AppleAuthService appleAuthService;

    @PostMapping("/google")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody GoogleAuthRequest req) {
        try {
            JwtAuthResponse tokens = authService.authenticateWithGoogle(req);
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            log.warn("Google authentication failed: {}", e.getClass().getSimpleName());
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
        try {
            JwtAuthResponse tokens = appleAuthService.authenticateWithApple(req);
            return ResponseEntity.ok(tokens);
        } catch (SecurityException e) {
            log.warn("Apple authentication rejected: {}", e.getClass().getSimpleName());
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            log.warn("Apple authentication failed: {}", e.getClass().getSimpleName());
            return ResponseEntity.badRequest().body("Apple authentication failed: " + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtAuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest req) throws Exception {
        JwtAuthResponse tokens = authService.refreshAccessToken(req);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revokeToken(@RequestBody RefreshTokenRequest request) {
        authService.revokeRefreshToken(request);
        return ResponseEntity.noContent().build();
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
