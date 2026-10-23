package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Informations minimales affichées sur la page d'activation (e-mail masqué).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationPreviewResponse {
    private String prenom;
    private String maskedEmail;
}
