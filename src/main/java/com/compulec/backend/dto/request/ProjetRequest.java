package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour la création ou mise à jour d'un Projet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjetRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String titre;

    private String sousTitre;

    @NotBlank(message = "La catégorie est obligatoire")
    private String categorie;

    private String service;

    private String clientName;

    @NotBlank(message = "Le secteur est obligatoire")
    private String secteur;

    @NotBlank(message = "La localisation est obligatoire")
    private String localisation;

    private Double latitude;

    private Double longitude;

    private String status;

    @NotBlank(message = "L'aperçu (overview) est obligatoire")
    private String overview;

    @NotBlank(message = "Le contexte est obligatoire")
    private String contexte;

    private List<String> besoins;

    @NotBlank(message = "La solution est obligatoire")
    private String solution;

    private List<String> technologies;

    @NotBlank(message = "Le résultat est obligatoire")
    private String resultat;

    private List<String> images;
}
