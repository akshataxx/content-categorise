package com.app.categorise.domain.service;

import com.app.categorise.api.dto.auth.GoogleAuthRequest;
import com.app.categorise.api.dto.auth.JwtAuthResponse;
import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.repository.UserRepository;
import com.app.categorise.domain.model.User;
import com.app.categorise.security.jwt.JwtTokenProvider;
//import com.google.api.client.auth.oauth2.RefreshTokenRequest;
import com.app.categorise.api.dto.auth.RefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
            UserEntity newUser = new UserEntity();
            newUser.setSub(userId);
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setFirstName(firstName);
            newUser.setLastName(lastName);
            newUser.setPictureUrl(pictureUrl);
            return userRepository.save(newUser);
        });

        User user = new User(userEntity.getId(), userEntity.getName(), userEntity.getEmail(), userEntity.getPictureUrl());

        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, null);
        System.out.println("Authentication: " + authentication);


        /*String jwt = tokenProvider.generateToken(authentication);
        System.out.println("Generated JWT: " + jwt);
        return jwt;*/

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

        //Extract User id from refresh jwt
        String userIdStr = tokenProvider.getUserIdFromJWT(incomingRefresh);
        UUID userId = UUID.fromString(userIdStr);

        //Load user entity
        UserEntity ue = userRepository.findById(userId)
                           .orElseThrow(() -> new RuntimeException("User not found"));

        //Build spring auth
        User user = new User(ue.getId(), ue.getName(), ue.getEmail(), ue.getPictureUrl());
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, null);

        //Issue a fresh access token
        String newAccess = tokenProvider.generateToken(auth);

        //Return both the access token and refresh token
        return new JwtAuthResponse(newAccess, incomingRefresh);
    }
} 