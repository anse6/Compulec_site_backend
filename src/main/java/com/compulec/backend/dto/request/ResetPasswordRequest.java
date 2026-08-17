package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour réinitialiser le mot de passe (mot de passe et sa confirmation uniquement).
 * L'identification de l'utilisateur se fait via le token JWT temporaire fourni dans le header d'autorisation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = 8, message = "Le nouveau mot de passe doit contenir au moins 8 caractères")
    private String newPassword;

    @NotBlank(message = "La confirmation du nouveau mot de passe est obligatoire")
    private String confirmNewPassword;
}
