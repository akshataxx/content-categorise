package com.app.categorise.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * store transcripts and meta data in postgres db
 */
@Entity
@Data
public class TranscriptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    private String category;

    private LocalDateTime createdAt = LocalDateTime.now();
}
