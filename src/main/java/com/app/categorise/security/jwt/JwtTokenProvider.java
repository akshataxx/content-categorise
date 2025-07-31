package com.app.categorise.security.jwt;

import com.app.categorise.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationInMs}")
    private int jwtExpirationInMs;

    @Value("${app.jwtRefreshExpirationInMs}")
    private long jwtRefreshExpirationInMs;

    private Key key;

    public String generateToken(Authentication authentication) {
        User userPrincipal = (User) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);
        
        if (key == null) {
            key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        }

        return Jwts.builder()
            .setSubject(userPrincipal.getId().toString())
            .setIssuedAt(new Date())
            .setExpiration(expiryDate)
            .signWith(key, SignatureAlgorithm.HS512)
            .compact();
    }

    public String generateRefreshToken(Authentication authentication) {
        User userPrincipal = (User) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtRefreshExpirationInMs);

        if (key == null) {
            key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        }

        return Jwts.builder()
                .setSubject(userPrincipal.getId().toString())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUserIdFromJWT(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }
} 