package com.compulec.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO représentant un message individuel dans l'historique d'une session de chat.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageResponse {

    private Long id;
    /** Type d'expéditeur : VISITOR, BOT ou ADMIN */
    private String senderType;
    private String content;
    /** Prénom du membre de l'équipe qui a répondu (messages ADMIN uniquement). */
    private String authorName;
    private LocalDateTime createdAt;
}
