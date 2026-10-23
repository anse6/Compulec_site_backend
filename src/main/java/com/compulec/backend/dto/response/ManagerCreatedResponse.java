package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse à la création (ou au renvoi) d'une invitation.
 * Le code d'activation n'est renvoyé qu'ici, une seule fois, pour être transmis
 * au manager par un autre canal que l'e-mail (de vive voix, appel, message).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerCreatedResponse {
    private UserResponse user;
    private String activationCode;
    private LocalDateTime invitationExpiresAt;
    private boolean emailSent;
}
