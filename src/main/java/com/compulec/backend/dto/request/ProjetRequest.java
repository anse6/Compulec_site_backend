package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    @NotBlank(message = "Le secteur est obligatoire")
    private String secteur;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotNull(message = "La date du projet est obligatoire")
    private LocalDate date;

    @NotBlank(message = "La localisation est obligatoire")
    private String localisation;

    private List<String> images;
}
