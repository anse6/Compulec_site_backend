package com.compulec.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant un Projet.
 */
@Entity
@Table(name = "projets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Projet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "titre", nullable = false, length = 150)
    private String titre;

    @Column(name = "sous_titre", length = 200)
    private String sousTitre;

    @Column(name = "categorie", nullable = false, length = 100)
    private String categorie;

    /** Un ou plusieurs services du catalogue, séparés par ", ". */
    @Column(name = "service", length = 500)
    private String service;

    @Column(name = "client_name", length = 150)
    private String clientName;

    @Column(name = "secteur", nullable = false, length = 100)
    private String secteur;

    @Column(name = "localisation", nullable = false, length = 255)
    private String localisation;

    /** Coordonnées GPS choisies sur la carte (facultatives pour les anciens projets). */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "status", length = 50)
    private String status;

    @Lob
    @Column(name = "overview", columnDefinition = "LONGTEXT")
    private String overview;

    @Lob
    @Column(name = "contexte", columnDefinition = "LONGTEXT")
    private String contexte;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "projet_besoins", joinColumns = @JoinColumn(name = "projet_id"))
    @Column(name = "besoin")
    @Builder.Default
    private List<String> besoins = new ArrayList<>();

    @Lob
    @Column(name = "solution", columnDefinition = "LONGTEXT")
    private String solution;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "projet_technologies", joinColumns = @JoinColumn(name = "projet_id"))
    @Column(name = "technologie")
    @Builder.Default
    private List<String> technologies = new ArrayList<>();

    @Lob
    @Column(name = "resultat", columnDefinition = "LONGTEXT")
    private String resultat;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "projet_images", joinColumns = @JoinColumn(name = "projet_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Column(name = "publie", nullable = false)
    @Builder.Default
    private boolean publie = false;

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
