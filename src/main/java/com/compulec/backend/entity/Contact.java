package com.compulec.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité représentant un message de contact envoyé via le formulaire de contact COMPULEC.
 */
@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "objet", nullable = false, length = 200)
    private String objet;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Statut du message : false = non lu, true = lu.
     */
    @Column(name = "lu", nullable = false)
    @Builder.Default
    private boolean lu = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt  = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
