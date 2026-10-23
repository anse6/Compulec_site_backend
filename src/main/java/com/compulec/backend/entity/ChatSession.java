package com.compulec.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité représentant une session de chat entre un visiteur et le bot/admin Compulec.
 * Chaque session est identifiée par un token UUID unique.
 */
@Entity
@Table(name = "chat_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom du visiteur (saisi au démarrage du chat) */
    @Column(name = "visitor_name", nullable = false, length = 100)
    private String visitorName;

    /** Email du visiteur (optionnel, saisi au démarrage) */
    @Column(name = "visitor_email", length = 150)
    private String visitorEmail;

    /**
     * Token UUID unique identifiant la session côté frontend.
     * Généré automatiquement à la création de la session.
     */
    @Column(name = "session_token", nullable = false, unique = true, length = 64)
    private String sessionToken;

    /** Nombre de questions posées par le visiteur au bot Chatling */
    @Column(name = "question_count", nullable = false)
    @Builder.Default
    private int questionCount = 0;

    /**
     * Statut de la session :
     * ACTIVE → en cours avec le bot
     * WAITING_ADMIN → visiteur a demandé un transfert vers admin
     * WITH_ADMIN → admin a pris en charge
     * CLOSED → session terminée
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ChatSessionStatus status = ChatSessionStatus.ACTIVE;

    /**
     * Admin (User avec rôle ADMIN) assigné à cette session.
     * Null tant qu'aucun admin n'a pris la main.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    private User assignedAdmin;

    /** Date du dernier message (visiteur, bot ou équipe) : sert au tri des conversations. */
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    /** Début du dernier message, affiché dans la liste des conversations. */
    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    /** Messages du visiteur non encore lus par l'équipe (remis à zéro à la lecture ou à la réponse). */
    @Column(name = "unread_count", nullable = false, columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private int unreadCount = 0;

    /** Langue du site au démarrage de la conversation ("fr", "en"…) : message d'accueil et réponses de l'IA. */
    @Column(name = "language", length = 5)
    private String language;

    /** Date du rappel envoyé quand personne n'a pris en charge une demande de conseiller. */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    /** Identifiant de la conversation côté Chatling (mémoire du contexte entre les messages). */
    @Column(name = "chatling_conversation_id", length = 40)
    private String chatlingConversationId;

    /** Nombre de réponses de l'assistant IA (plafonné pour maîtriser les coûts). */
    @Column(name = "ai_reply_count", nullable = false, columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private int aiReplyCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
