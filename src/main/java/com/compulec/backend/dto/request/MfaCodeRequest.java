package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Étape 2FA : jeton 2FA reçu au login + code de l'application (6 chiffres) ou code de secours.
 * mfaToken est optionnel pour les endpoints où l'utilisateur est déjà connecté.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaCodeRequest {

    private String mfaToken;

    @NotBlank(message = "Le code est obligatoire")
    @Size(max = 20, message = "Code invalide")
    private String code;
}
