package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO retourné pour les requêtes concernant les projets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjetResponse {

    private Long id;
    private String titre;
    private String sousTitre;
    private String categorie;
    private String service;
    private String clientName;
    private String secteur;
    private String localisation;
    private Double latitude;
    private Double longitude;
    private String status;
    private String overview;
    private String contexte;
    private List<String> besoins;
    private String solution;
    private List<String> technologies;
    private String resultat;
    private List<String> images;
    private boolean publie;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
