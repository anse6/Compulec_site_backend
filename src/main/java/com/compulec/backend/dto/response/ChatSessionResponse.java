package com.compulec.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO représentant une session de chat complète avec son historique.
 * Utilisé pour l'historique visiteur et le dashboard admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatSessionResponse {

    private Long id;
    private String sessionToken;
    private String visitorName;
    private String visitorEmail;
    private int questionCount;
    private String status;

    /** Nom complet de l'admin assigné (si applicable) */
    private String assignedAdminName;

    /** Email de l'admin assigné (si applicable) */
    private String assignedAdminEmail;

    /** Historique complet des messages de la session */
    private List<ChatMessageResponse> messages;

    private int unreadCount;
    private String lastMessagePreview;
    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
