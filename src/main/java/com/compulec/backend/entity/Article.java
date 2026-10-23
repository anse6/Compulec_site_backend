package com.compulec.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité représentant un Article (News).
 */
@Entity
@Table(name = "articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "subtitle", length = 255)
    private String subtitle;

    @Column(name = "heading", length = 255)
    private String heading;

    @Lob
    @Column(name = "overview", columnDefinition = "LONGTEXT")
    private String overview;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
