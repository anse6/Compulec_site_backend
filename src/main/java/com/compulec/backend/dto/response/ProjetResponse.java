package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    private String secteur;
    private String description;
    private LocalDate date;
    private List<String> images;
    private String localisation;
    private boolean publie;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
