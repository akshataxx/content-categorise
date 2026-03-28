package com.app.categorise.domain.service;

import com.app.categorise.api.dto.auth.AppleAuthRequest;
import com.app.categorise.api.dto.auth.JwtAuthResponse;
import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.data.repository.UserRepository;
import com.app.categorise.security.jwt.JwtTokenProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class AppleAuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    @org.springframework.beans.factory.annotation.Value("${app.jwtRefreshExpirationInMs}")
    private long jwtRefreshExpirationInMs;

    private static final String APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    /**
     * Authenticates user with Apple Sign-In
     * 1. Verifies the identity token with Apple's public keys
     * 2. Extracts user information from the token
     * 3. Creates or finds user in database
     * 4. Returns JWT tokens
     */
    public JwtAuthResponse authenticateWithApple(AppleAuthRequest request) throws Exception {
        // 1. Verify and decode the Apple identity token
        SignedJWT signedJWT = SignedJWT.parse(request.getIdentityToken());
        
        // 2. Verify token signature with Apple's public keys
        if (!verifyAppleToken(signedJWT)) {
            throw new SecurityException("Invalid Apple token signature");
        }

        // 3. Verify token claims
        String appleUserId = signedJWT.getJWTClaimsSet().getSubject();
        String issuer = signedJWT.getJWTClaimsSet().getIssuer();
        Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();

        // Verify issuer
        if (!APPLE_ISSUER.equals(issuer)) {
            throw new SecurityException("Invalid token issuer");
        }

        // Verify expiration
        if (expirationTime.before(new Date())) {
            throw new SecurityException("Token has expired");
        }

        // 4. Get email from token (if available) or from request
        String email = (String) signedJWT.getJWTClaimsSet().getClaim("email");
        if (email == null || email.isEmpty()) {
            email = request.getEmail();
        }

        // 5. Find or create user
        UserEntity user = findOrCreateAppleUser(
            appleUserId,
            email,
            request.getFirstName(),
            request.getLastName()
        );

        // 6. Generate JWT tokens (matching existing AuthService pattern)
        com.app.categorise.domain.model.User principal = new com.app.categorise.domain.model.User(
            user.getId(), 
            user.getName(), 
            user.getEmail(), 
            user.getPictureUrl()
        );
        
        org.springframework.security.core.Authentication authentication = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, null);
        
        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        
        // Persist refresh token in database
        refreshTokenService.save(
            user.getId(), 
            refreshToken, 
            java.time.Instant.now().plusMillis(jwtRefreshExpirationInMs)
        );

        return new JwtAuthResponse(accessToken, refreshToken);
    }

    /**
     * Verifies the Apple JWT token signature using Apple's public keys
     */
    private boolean verifyAppleToken(SignedJWT signedJWT) {
        try {
            // Fetch Apple's public keys
            RestTemplate restTemplate = new RestTemplate();
            String jwksJson = restTemplate.getForObject(APPLE_PUBLIC_KEYS_URL, String.class);
            JWKSet jwkSet = JWKSet.parse(jwksJson);

            // Get the key ID from the JWT header
            String keyId = signedJWT.getHeader().getKeyID();

            // Find the matching public key
            JWK jwk = jwkSet.getKeyByKeyId(keyId);
            if (jwk == null) {
                System.err.println("No matching key found for keyId: " + keyId);
                return false;
            }

            // Create verifier with the public key
            RSAKey rsaKey = (RSAKey) jwk;
            JWSVerifier verifier = new RSASSAVerifier(rsaKey);

            // Verify the signature
            return signedJWT.verify(verifier);

        } catch (Exception e) {
            System.err.println("Error verifying Apple token: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Finds existing user by Apple user ID or creates a new one
     */
    private UserEntity findOrCreateAppleUser(
        String appleUserId,
        String email,
        String firstName,
        String lastName
    ) {
        // Try to find by Apple user ID first
        Optional<UserEntity> existingUser = userRepository.findByAppleUserId(appleUserId);
        
        if (existingUser.isPresent()) {
            return existingUser.get();
        }
        
        // Check if user exists with same email (link accounts)
        if (email != null && !email.isEmpty()) {
            Optional<UserEntity> emailUser = userRepository.findByEmail(email);
            if (emailUser.isPresent()) {
                UserEntity existing = emailUser.get();
                existing.setAppleUserId(appleUserId);
                if (existing.getFirstName() == null) existing.setFirstName(firstName);
                if (existing.getLastName() == null) existing.setLastName(lastName);
                if (existing.getName() == null) {
                    existing.setName(buildFullName(firstName, lastName));
                }
                return userRepository.save(existing);
            }
        }

        // Create new user
        UserEntity newUser = new UserEntity();
        newUser.setAppleUserId(appleUserId);
        newUser.setEmail(email); // NULL if user hides email via Apple
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setName(buildFullName(firstName, lastName));
        newUser.setCreatedAt(Instant.now());

        UserEntity savedUser = userRepository.save(newUser);
        
        // Initialize free subscription for new user
        subscriptionService.initializeFreeSubscription(savedUser.getId());

        return savedUser;
    }
    
    private String buildFullName(String firstName, String lastName) {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return "Apple User";
    }
}
