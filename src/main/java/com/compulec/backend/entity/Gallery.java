package com.compulec.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "galleries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gallery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "projet_id", nullable = false)
    private Projet projet;

    @Column(name = "titre", length = 150)
    private String titre;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gallery_images", joinColumns = @JoinColumn(name = "gallery_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
