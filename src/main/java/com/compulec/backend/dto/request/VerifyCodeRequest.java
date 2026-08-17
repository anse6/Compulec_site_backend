package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour vérifier le code de réinitialisation reçu par email (code seul).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyCodeRequest {

    @NotBlank(message = "Le code de vérification est obligatoire")
    @Pattern(regexp = "^[0-9]{6}$", message = "Le code doit contenir exactement 6 chiffres")
    private String code;
}
