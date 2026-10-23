package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO contenant uniquement un jeton (transmis dans le corps, jamais dans l'URL).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRequest {

    @NotBlank(message = "Le jeton est obligatoire")
    private String token;
}
