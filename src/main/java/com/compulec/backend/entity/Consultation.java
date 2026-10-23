package com.compulec.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité représentant une demande de consultation via le formulaire.
 */
@Entity
@Table(name = "consultations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "company", length = 150)
    private String company;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "service_of_interest", length = 150)
    private String serviceOfInterest;

    @Column(name = "project_description", nullable = false, columnDefinition = "TEXT")
    private String projectDescription;

    @Column(name = "preferred_contact", length = 50)
    private String preferredContact;

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
