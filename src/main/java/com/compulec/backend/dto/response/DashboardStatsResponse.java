package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO retourné par l'endpoint GET /dashboard/stats.
 * Contient tous les compteurs affichés dans les cartes du tableau de bord.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    /** Nombre de projets publiés (publie = true) */
    private long publishedProjects;

    /** Nombre total de projets */
    private long totalProjects;

    /** Nombre de messages de contact non lus */
    private long unreadMessages;

    /** Nombre total de messages de contact */
    private long totalMessages;

    /** Nombre de sessions AI actives ou en attente de prise en charge (ACTIVE + WAITING_ADMIN + WITH_ADMIN) */
    private long activeChatSessions;

    /** Nombre total de sessions AI */
    private long totalChatSessions;

    /** Nombre d'administrateurs actifs (rôle ADMIN, compte non désactivé) */
    private long adminCount;

    /** Nombre de membres de l'équipe actifs et activés (ADMIN + MANAGER) */
    private long activeStaff;

    /** Nombre total d'utilisateurs */
    private long totalUsers;

    /** Nombre d'articles/news publiés */
    private long publishedArticles;

    /** Nombre total d'articles/news */
    private long totalArticles;
}
