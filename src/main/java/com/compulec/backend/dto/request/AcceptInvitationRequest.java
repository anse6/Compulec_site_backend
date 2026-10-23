package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour accepter une invitation : jeton du lien + code transmis par l'admin + mot de passe choisi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceptInvitationRequest {

    @NotBlank(message = "Le jeton d'invitation est obligatoire")
    private String token;

    @NotBlank(message = "Le code d'activation est obligatoire")
    @Pattern(regexp = "^[0-9]{6}$", message = "Le code d'activation doit contenir exactement 6 chiffres")
    private String code;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String password;

    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmPassword;
}
