package com.compulec.backend.controller;

import com.compulec.backend.dto.request.AdminChatReplyRequest;
import com.compulec.backend.dto.request.ChatMessageRequest;
import com.compulec.backend.dto.request.ChatStartRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.ChatBotReplyResponse;
import com.compulec.backend.dto.response.ChatSessionResponse;
import com.compulec.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.compulec.backend.chat.ChatStreamService;
import com.compulec.backend.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur REST pour le module de chat Compulec.
 *
 * Deux types d'endpoints :
 * 1. Endpoints PUBLICS (/chat/start, /chat/message, /chat/request-admin, /chat/history/**)
 *    → Accessibles aux visiteurs anonymes, aucune authentification requise
 *
 * 2. Endpoints ADMIN (/chat/admin/**)
 *    → Réservés aux utilisateurs avec rôle ADMIN ou MANAGER (authentification JWT requise)
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Module de chat Chatling avec escalade vers admin Compulec")
public class ChatController {

    private final ChatService chatService;
    private final ChatStreamService chatStreamService;

    // =========================================================================
    // ENDPOINTS PUBLICS – Visiteur anonyme
    // =========================================================================

    /**
     * Démarre une nouvelle session de chat.
     * Le visiteur fournit son nom (et optionnellement son email).
     * En retour, il reçoit un sessionToken UUID à conserver pour la suite.
     */
    @PostMapping("/start")
    @Operation(
        summary = "Démarrer une session de chat",
        description = "Crée une nouvelle session de chat pour un visiteur anonyme. " +
                      "Retourne un sessionToken à utiliser dans tous les appels suivants."
    )
    public ResponseEntity<ApiResponse<ChatSessionResponse>> startChat(
            @Valid @RequestBody ChatStartRequest request) {

        ChatSessionResponse session = chatService.startSession(request);
        return ResponseEntity.ok(ApiResponse.success(session,
                "Session de chat démarrée avec succès. Bienvenue " + request.getVisitorName() + " !"));
    }

    /**
     * Envoie un message du visiteur et reçoit la réponse du bot Chatling.
     * Si questionCount >= 5, le champ showAdminTransfer est true
     * → le frontend doit afficher le dialog de proposition de transfert vers admin.
     */
    @PostMapping("/message")
    @Operation(
        summary = "Envoyer un message au bot",
        description = "Envoie le message du visiteur à Chatling et retourne la réponse du bot. " +
                      "Quand questionCount >= 5, showAdminTransfer = true pour déclencher le dialog côté frontend."
    )
    public ResponseEntity<ApiResponse<ChatBotReplyResponse>> sendMessage(
            @Valid @RequestBody ChatMessageRequest request) {

        ChatBotReplyResponse reply = chatService.sendMessage(request);
        return ResponseEntity.ok(ApiResponse.success(reply, "Message envoyé avec succès"));
    }

    /**
     * Demande le transfert de la session vers un admin humain.
     * Déclenche l'envoi d'un email de notification au premier admin actif.
     * Appelé quand le visiteur accepte la proposition de transfert.
     */
    @PostMapping("/request-admin/{sessionToken}")
    @Operation(
        summary = "Demander un transfert vers un admin",
        description = "Met la session en statut WAITING_ADMIN et prévient par e-mail tous les admins et managers actifs. " +
                      "À appeler quand le visiteur accepte le dialog de transfert."
    )
    public ResponseEntity<ApiResponse<ChatSessionResponse>> requestAdmin(
            @PathVariable String sessionToken) {

        ChatSessionResponse session = chatService.requestAdminTransfer(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(session,
                "Un conseiller Compulec a été notifié. Veuillez patienter."));
    }

    /**
     * Récupère l'historique complet des messages d'une session.
     * Utilisé par le frontend pour afficher la conversation en polling.
     */
    @GetMapping("/history/{sessionToken}")
    @Operation(
        summary = "Obtenir l'historique de la session",
        description = "Retourne la session de chat avec l'intégralité des messages dans l'ordre chronologique. " +
                      "Le frontend peut appeler cet endpoint en polling pour détecter les nouvelles réponses admin."
    )
    public ResponseEntity<ApiResponse<ChatSessionResponse>> getHistory(
            @PathVariable String sessionToken) {

        ChatSessionResponse session = chatService.getSessionHistory(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    @GetMapping(value = "/stream/{sessionToken}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Temps réel (visiteur)",
        description = "Flux Server-Sent Events : un événement « chat » est envoyé à chaque nouveau message ou changement " +
                      "de statut ; le widget recharge alors l'historique."
    )
    public SseEmitter visitorStream(@PathVariable String sessionToken) {
        chatService.assertSessionExists(sessionToken);
        return chatStreamService.subscribeVisitor(sessionToken);
    }

    // =========================================================================
    // ENDPOINTS ÉQUIPE – Admins et managers (JWT requis)
    // =========================================================================

    @GetMapping(value = "/admin/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ÉQUIPE] Temps réel",
        description = "Flux Server-Sent Events de tous les événements du chat (nouvelle demande, message, prise en charge…). " +
                      "Fermé après 15 minutes : le client se reconnecte avec un jeton valide."
    )
    public SseEmitter staffStream(@AuthenticationPrincipal User user) {
        return chatStreamService.subscribeStaff(user.getId());
    }

    @GetMapping("/admin/sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ÉQUIPE] Conversations par statut",
        description = "status = ACTIVE (avec le bot), WAITING_ADMIN, WITH_ADMIN ou CLOSED. " +
                      "Triées par dernière activité, paginées (20 par défaut, 50 max), recherche facultative " +
                      "sur le nom, l'email ou le numéro de session (q)."
    )
    public ResponseEntity<ApiResponse<Page<ChatSessionResponse>>> searchSessions(
            @RequestParam String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(chatService.searchSessions(status, q, page, size)));
    }

    @GetMapping("/admin/sessions/counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ÉQUIPE] Compteurs",
        description = "Nombre de conversations par statut et total des messages visiteurs non lus (UNREAD)."
    )
    public ResponseEntity<ApiResponse<Map<String, Long>>> countSessions() {
        return ResponseEntity.ok(ApiResponse.success(chatService.countSessions()));
    }

    @GetMapping("/admin/sessions/{sessionToken}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ÉQUIPE] Détail d'une session",
        description = "Retourne la session complète avec l'historique de tous les messages et leurs auteurs."
    )
    public ResponseEntity<ApiResponse<ChatSessionResponse>> getSessionDetail(
            @PathVariable String sessionToken) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getSessionForStaff(sessionToken)));
    }

    @PostMapping("/admin/take/{sessionToken}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ÉQUIPE] Prendre en charge",
        description = "Le membre connecté devient le conseiller de la conversation (son prénom est affiché au visiteur). " +
                      "Possible depuis une conversation en attente, avec le bot, ou tenue par un collègue."
    )
    public ResponseEntity<ApiResponse<ChatSessionResponse>> takeOver(
            @PathVariable String sessionToken,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                chatService.takeOver(sessionToken, authentication.getName()), "Conversation prise en charge"));
    }

    @PostMapping("/admin/reply")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ÉQUIPE] Répondre dans une session",
        description = "Répond au visiteur (message signé). Prend automatiquement la conversation en charge si besoin."
    )
    public ResponseEntity<ApiResponse<ChatSessionResponse>> adminReply(
            @Valid @RequestBody AdminChatReplyRequest request,
            Authentication authentication) {
        ChatSessionResponse session = chatService.adminReply(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(session, "Message envoyé avec succès"));
    }

    @PatchMapping("/admin/read/{sessionToken}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "[ÉQUIPE] Marquer comme lu", description = "Remet à zéro le compteur de messages non lus.")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String sessionToken) {
        chatService.markAsRead(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(null, "Conversation marquée comme lue"));
    }

    @PatchMapping("/admin/close/{sessionToken}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ÉQUIPE] Fermer une session",
        description = "Met la session en statut CLOSED et prévient le visiteur, qui peut en démarrer une nouvelle."
    )
    public ResponseEntity<ApiResponse<Void>> closeSession(
            @PathVariable String sessionToken,
            Authentication authentication) {
        chatService.closeSession(sessionToken, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "Session de chat fermée avec succès."));
    }
}
