package com.app.categorise.data.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private String token;
    private Instant expiryDate;
}