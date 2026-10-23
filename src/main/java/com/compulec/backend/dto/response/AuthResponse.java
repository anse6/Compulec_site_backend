package com.compulec.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse d'authentification.
 *
 * Deux formes :
 *  - session ouverte : access_token + user (le refresh token est dans un cookie HttpOnly, jamais dans le JSON) ;
 *    si la 2FA n'est pas activée : mfa_setup_available + mfa_token pour proposer de l'activer ;
 *  - 2FA activée : mfa_required + mfa_token (valable 5 minutes, sans aucun droit) avant toute session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;

    private UserResponse user;

    @JsonProperty("mfa_required")
    private Boolean mfaRequired;

    /** Connexion réussie sans 2FA : on propose de l'activer (facultatif). */
    @JsonProperty("mfa_setup_available")
    private Boolean mfaSetupAvailable;

    @JsonProperty("mfa_token")
    private String mfaToken;

    /** Codes de secours, renvoyés une seule fois à l'activation de la 2FA. */
    @JsonProperty("backup_codes")
    private List<String> backupCodes;
}
