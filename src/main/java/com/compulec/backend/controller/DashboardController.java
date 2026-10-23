package com.compulec.backend.controller;

import com.compulec.backend.dto.response.*;
import com.compulec.backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST pour le tableau de bord administrateur.
 * Tous les endpoints nécessitent un rôle ADMIN ou MANAGER.
 * Le paramètre {@code limit} est ramené entre 1 et 50 par le service.
 *
 * Endpoints :
 *  GET /dashboard/stats              → Compteurs statistiques (cartes)
 *  GET /dashboard/recent-messages    → Derniers messages de contact
 *  GET /dashboard/ai-sessions        → Conversations non terminées
 *  GET /dashboard/recent-activity    → Flux d'activité récente
 *  GET /dashboard/interactions-chart → Créations par mois (6 derniers mois)
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@Tag(name = "Dashboard", description = "Endpoints pour alimenter le tableau de bord administrateur")
public class DashboardController {

    private final DashboardService dashboardService;

    // =========================================================================
    // GET /api/dashboard/stats
    // =========================================================================
    @GetMapping("/stats")
    @Operation(
            summary = "Statistiques du tableau de bord",
            description = "Retourne les compteurs temps réel pour les cartes du dashboard : projets publiés, messages non lus, sessions AI, équipe, articles."
    )
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStats()));
    }

    // =========================================================================
    // GET /api/dashboard/recent-messages?limit=6
    // =========================================================================
    @GetMapping("/recent-messages")
    @Operation(
            summary = "Derniers messages de contact",
            description = "Retourne les N derniers messages du formulaire de contact, triés par date décroissante."
    )
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getRecentMessages(
            @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getRecentMessages(limit)));
    }

    // =========================================================================
    // GET /api/dashboard/ai-sessions?limit=8
    // =========================================================================
    @GetMapping("/ai-sessions")
    @Operation(
            summary = "Sessions AI actives / transférées",
            description = "Retourne les N conversations non terminées les plus récemment actives."
    )
    public ResponseEntity<ApiResponse<List<ChatSessionResponse>>> getAiSessions(
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getAiSessions(limit)));
    }

    // =========================================================================
    // GET /api/dashboard/recent-activity?limit=10
    // =========================================================================
    @GetMapping("/recent-activity")
    @Operation(
            summary = "Activité récente",
            description = "Retourne un flux agrégé des N dernières actions (projets, messages, articles, galerie, sessions chat)."
    )
    public ResponseEntity<ApiResponse<List<DashboardActivityResponse>>> getRecentActivity(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getRecentActivity(limit)));
    }

    // =========================================================================
    // GET /api/dashboard/interactions-chart
    // =========================================================================
    @GetMapping("/interactions-chart")
    @Operation(
            summary = "Données du graphique d'interactions",
            description = "Retourne, pour chacun des 6 derniers mois (format yyyy-MM), le nombre de messages, chats, projets et articles créés."
    )
    public ResponseEntity<ApiResponse<List<InteractionChartResponse>>> getInteractionChartData() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getInteractionChartData()));
    }
}
