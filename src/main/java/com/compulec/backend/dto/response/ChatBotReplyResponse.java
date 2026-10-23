package com.compulec.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de réponse après l'envoi d'un message au chat.
 * Contient la réponse du bot Chatling, le compteur de questions
 * et un indicateur pour afficher le dialog de transfert vers admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatBotReplyResponse {

    /** Token UUID de la session de chat */
    private String sessionToken;

    /** Réponse générée par le bot Chatling */
    private String botReply;

    /** Nombre total de questions posées par le visiteur */
    private int questionCount;

    /**
     * true si le visiteur a posé >= 5 questions.
     * Le frontend doit afficher le dialog de transfert vers admin.
     */
    private boolean showAdminTransfer;

    /** Statut actuel de la session : ACTIVE, WAITING_ADMIN, WITH_ADMIN, CLOSED */
    private String sessionStatus;
}
