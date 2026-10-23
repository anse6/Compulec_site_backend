package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Données d'enrôlement 2FA : URI à afficher en QR code + secret pour la saisie manuelle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupResponse {
    private String otpauthUri;
    private String secret;
}
