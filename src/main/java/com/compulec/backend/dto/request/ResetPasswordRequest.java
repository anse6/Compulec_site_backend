package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour réinitialiser le mot de passe à partir du lien reçu par e-mail.
 * L'utilisateur est identifié par le jeton à usage unique du lien.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Le jeton de réinitialisation est obligatoire")
    private String token;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    private String newPassword;

    @NotBlank(message = "La confirmation du nouveau mot de passe est obligatoire")
    private String confirmNewPassword;
}
