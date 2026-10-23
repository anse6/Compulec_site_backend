package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO pour changer le mot de passe de l'utilisateur connecté.
 */
@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "L'ancien mot de passe est obligatoire")
    private String ancienMotDePasse;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    private String nouveauMotDePasse;

    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmNouveauMotDePasse;
}
