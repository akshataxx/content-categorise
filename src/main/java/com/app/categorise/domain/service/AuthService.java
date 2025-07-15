package com.app.categorise.domain.service;

import com.app.categorise.api.dto.auth.GoogleAuthRequest;
import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.repository.UserRepository;
import com.app.categorise.domain.model.User;
import com.app.categorise.security.jwt.JwtTokenProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;
    
    private final JsonFactory jsonFactory = new GsonFactory();

    public String authenticateWithGoogle(GoogleAuthRequest googleAuthRequest) throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), jsonFactory)
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(googleAuthRequest.getIdToken());
        if (idToken == null) {
            throw new Exception("Invalid Google ID token.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String userId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");

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
        return tokenProvider.generateToken(authentication);
    }
} 