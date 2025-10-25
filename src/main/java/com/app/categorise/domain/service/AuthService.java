package com.app.categorise.domain.service;

import com.app.categorise.api.dto.auth.GoogleAuthRequest;
import com.app.categorise.api.dto.auth.JwtAuthResponse;
import com.app.categorise.api.dto.auth.LoginRequest;
import com.app.categorise.api.dto.auth.RegisterRequest;
import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.repository.UserRepository;
import com.app.categorise.domain.model.User;
import com.app.categorise.security.jwt.JwtTokenProvider;
import com.app.categorise.api.dto.auth.RefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${app.jwtRefreshExpirationInMs}")
    private long jwtRefreshExpirationInMs;
    
    private final JsonFactory jsonFactory = new GsonFactory();

    public JwtAuthResponse authenticateWithGoogle(GoogleAuthRequest req) throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), jsonFactory)
            .setAudience(Collections.singletonList(googleClientId))
            .build();

        System.out.println("Google Client ID: " + googleClientId);
        System.out.println("Google Auth Request: " + req.getIdToken());

        GoogleIdToken idToken = verifier.verify(req.getIdToken());
        if (idToken == null) {
            System.out.println("Invalid Google ID token.");
            throw new Exception("Invalid Google ID token.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String userId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");

        System.out.println("Validated User ID: " + userId);

        UserEntity userEntity = userRepository.findBySub(userId).orElseGet(() -> {
            // If a local account exists with the same email, link it by setting sub
            return userRepository.findByEmail(email).map(existing -> {
                existing.setSub(userId);
                existing.setName(existing.getName() != null ? existing.getName() : name);
                if (existing.getFirstName() == null) existing.setFirstName(firstName);
                if (existing.getLastName() == null) existing.setLastName(lastName);
                if (existing.getPictureUrl() == null) existing.setPictureUrl(pictureUrl);
                return userRepository.save(existing);
            }).orElseGet(() -> {
                UserEntity newUser = new UserEntity();
                newUser.setSub(userId);
                newUser.setEmail(email);
                newUser.setName(name);
                newUser.setFirstName(firstName);
                newUser.setLastName(lastName);
                newUser.setPictureUrl(pictureUrl);
                return userRepository.save(newUser);
            });
        });

        User user = new User(userEntity.getId(), userEntity.getName(), userEntity.getEmail(), userEntity.getPictureUrl());

        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, null);
        System.out.println("Authentication: " + authentication);

        //issue both access and refresh tokens at the same time
        String accessToken = tokenProvider.generateToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        //persist refresh token in the database
        refreshTokenService.save(userEntity.getId(), refreshToken, Instant.now().plusMillis(jwtRefreshExpirationInMs));

        //return both in DTO
        return new JwtAuthResponse(accessToken, refreshToken);
    }

    public JwtAuthResponse refreshAccessToken(RefreshTokenRequest req) throws Exception {
        String incomingRefresh = req.getRefreshToken();
        if (!refreshTokenService.isValid(incomingRefresh)) {
            throw new Exception("Invalid refresh token");
        }

        String userIdStr = tokenProvider.getUserIdFromJWT(incomingRefresh);
        UUID userId = UUID.fromString(userIdStr);

        UserEntity ue = userRepository.findById(userId)
                           .orElseThrow(() -> new RuntimeException("User not found"));

        User user = new User(ue.getId(), ue.getName(), ue.getEmail(), ue.getPictureUrl());
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, null);

        String newAccess = tokenProvider.generateToken(auth);

        return new JwtAuthResponse(newAccess, incomingRefresh);
    }

    public JwtAuthResponse register(RegisterRequest req) {
        // Enforce uniqueness
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        UserEntity user = new UserEntity();
        user.setEmail(req.getEmail());
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setName(req.getFirstName() != null ? req.getFirstName() + " " + (req.getLastName() != null ? req.getLastName() : "") : req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));

        UserEntity saved = userRepository.save(user);

        User principal = new User(saved.getId(), saved.getName(), saved.getEmail(), saved.getPictureUrl());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, null);

        String accessToken = tokenProvider.generateToken(auth);
        String refreshToken = tokenProvider.generateRefreshToken(auth);
        refreshTokenService.save(saved.getId(), refreshToken, Instant.now().plusMillis(jwtRefreshExpirationInMs));

        return new JwtAuthResponse(accessToken, refreshToken);
    }

    public JwtAuthResponse login(LoginRequest req) {
        UserEntity user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        User principal = new User(user.getId(), user.getName(), user.getEmail(), user.getPictureUrl());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, null);
        String accessToken = tokenProvider.generateToken(auth);
        String refreshToken = tokenProvider.generateRefreshToken(auth);
        refreshTokenService.save(user.getId(), refreshToken, Instant.now().plusMillis(jwtRefreshExpirationInMs));

        return new JwtAuthResponse(accessToken, refreshToken);
    }
} 