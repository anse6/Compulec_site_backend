package com.compulec.backend.service;

import com.compulec.backend.dto.response.ChatSessionResponse;
import com.compulec.backend.dto.response.ContactResponse;
import com.compulec.backend.dto.response.DashboardActivityResponse;
import com.compulec.backend.dto.response.DashboardStatsResponse;
import com.compulec.backend.dto.response.InteractionChartResponse;
import com.compulec.backend.entity.ChatSessionStatus;
import com.compulec.backend.entity.Role;
import com.compulec.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjLongConsumer;

/**
 * Service agrégeant les données de plusieurs entités pour alimenter le tableau de bord admin.
 * Tous les comptages sont faits en base (COUNT / GROUP BY) : aucune table n'est chargée en mémoire.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** Borne haute du paramètre {@code limit} des listes (protège contre ?limit=1000000). */
    static final int MAX_LIMIT = 50;

    /** Nombre de mois affichés par le graphique (mois courant inclus). */
    static final int CHART_MONTHS = 6;

    /** Conversations qui ne sont pas terminées. */
    private static final List<ChatSessionStatus> OPEN_CHAT_STATUSES =
            List.of(ChatSessionStatus.ACTIVE, ChatSessionStatus.WAITING_ADMIN, ChatSessionStatus.WITH_ADMIN);

    private final ProjetRepository projetRepository;
    private final ContactRepository contactRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final GalleryRepository galleryRepository;
    private final ContactService contactService;
    private final ChatService chatService;

    /**
     * Compteurs statistiques pour les cartes du dashboard.
     * GET /dashboard/stats
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        return DashboardStatsResponse.builder()
                .publishedProjects(projetRepository.countByPublieTrue())
                .totalProjects(projetRepository.count())
                .unreadMessages(contactRepository.countByLu(false))
                .totalMessages(contactRepository.count())
                .activeChatSessions(chatSessionRepository.countByStatusIn(OPEN_CHAT_STATUSES))
                .totalChatSessions(chatSessionRepository.count())
                .adminCount(userRepository.countByRoleAndActifTrue(Role.ADMIN))
                .activeStaff(userRepository.countByRoleInAndActifTrueAndActivatedAtIsNotNull(
                        List.of(Role.ADMIN, Role.MANAGER)))
                .totalUsers(userRepository.count())
                .publishedArticles(articleRepository.countByPublishedTrue())
                .totalArticles(articleRepository.count())
                .build();
    }

    /**
     * Derniers messages de contact (widget « Demandes à traiter »).
     * GET /dashboard/recent-messages
     */
    @Transactional(readOnly = true)
    public List<ContactResponse> getRecentMessages(int limit) {
        return contactRepository.findAll(latest(limit, "createdAt")).getContent().stream()
                .map(contactService::mapToResponse)
                .toList();
    }

    /**
     * Conversations non terminées, les plus récemment actives d'abord (widget « Conversations IA »).
     * GET /dashboard/ai-sessions
     */
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getAiSessions(int limit) {
        return chatSessionRepository.findRecentByStatusIn(OPEN_CHAT_STATUSES, PageRequest.of(0, clamp(limit)))
                .stream()
                .map(session -> chatService.toSessionResponse(session, false, true))
                .toList();
    }

    /**
     * Flux agrégé des N dernières activités (projets, messages, articles, galerie, chat).
     * Chaque source fournit jusqu'à N éléments : le résultat contient donc bien les N plus récents, toutes sources confondues.
     * GET /dashboard/recent-activity
     */
    @Transactional(readOnly = true)
    public List<DashboardActivityResponse> getRecentActivity(int limit) {
        int size = clamp(limit);
        List<DashboardActivityResponse> activities = new ArrayList<>();

        projetRepository.findAll(latest(size, "updatedAt")).forEach(p -> activities.add(
                activity("PROJECT", p.isPublie() ? "PUBLISHED" : "DRAFT", p.getId(), p.getTitre(), p.getUpdatedAt())));

        contactRepository.findAll(latest(size, "createdAt")).forEach(c -> activities.add(
                activity("MESSAGE", "RECEIVED", c.getId(), c.getNom(), c.getCreatedAt())));

        articleRepository.findAll(latest(size, "updatedAt")).forEach(a -> activities.add(
                activity("ARTICLE", a.isPublished() ? "PUBLISHED" : "DRAFT", a.getId(), a.getTitle(), a.getUpdatedAt())));

        galleryRepository.findAll(latest(size, "createdAt")).forEach(g -> activities.add(
                activity("GALLERY", "ADDED", g.getId(), g.getTitre(), g.getCreatedAt())));

        chatSessionRepository.findRecentByStatusIn(List.of(ChatSessionStatus.WAITING_ADMIN), PageRequest.of(0, size))
                .forEach(s -> activities.add(activity("CHAT", "TRANSFERRED", s.getId(), s.getVisitorName(),
                        s.getLastActivityAt() != null ? s.getLastActivityAt() : s.getCreatedAt())));

        return activities.stream()
                .sorted(Comparator.comparing(DashboardActivityResponse::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(size)
                .toList();
    }

    /**
     * Nombre d'éléments créés par mois sur les {@value #CHART_MONTHS} derniers mois (mois courant inclus).
     * Les mois sans activité sont présents avec des compteurs à 0.
     * GET /dashboard/interactions-chart
     */
    @Transactional(readOnly = true)
    public List<InteractionChartResponse> getInteractionChartData() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(CHART_MONTHS - 1L);
        LocalDateTime since = firstMonth.atDay(1).atStartOfDay();

        Map<YearMonth, InteractionChartResponse> byMonth = new LinkedHashMap<>();
        for (YearMonth ym = firstMonth; !ym.isAfter(currentMonth); ym = ym.plusMonths(1)) {
            byMonth.put(ym, InteractionChartResponse.builder().month(ym.toString()).build());
        }

        fill(byMonth, contactRepository.countCreatedPerMonthSince(since), InteractionChartResponse::setMessages);
        fill(byMonth, chatSessionRepository.countCreatedPerMonthSince(since), InteractionChartResponse::setChats);
        fill(byMonth, projetRepository.countCreatedPerMonthSince(since), InteractionChartResponse::setProjects);
        fill(byMonth, articleRepository.countCreatedPerMonthSince(since), InteractionChartResponse::setArticles);

        return new ArrayList<>(byMonth.values());
    }

    // =========================================================================
    // Utilitaires
    // =========================================================================

    /** Ramène {@code limit} dans [1, MAX_LIMIT] (PageRequest.of(0, 0) lèverait une exception → 500). */
    static int clamp(int limit) {
        return Math.clamp(limit, 1, MAX_LIMIT);
    }

    private static Pageable latest(int limit, String dateField) {
        return PageRequest.of(0, clamp(limit), Sort.by(Sort.Direction.DESC, dateField));
    }

    private static DashboardActivityResponse activity(String type, String action, Long targetId,
                                                      String subject, LocalDateTime date) {
        return DashboardActivityResponse.builder()
                .type(type)
                .action(action)
                .targetId(targetId)
                .subject(subject)
                .date(date)
                .build();
    }

    /** Reporte les lignes [année, mois, nombre] d'une requête GROUP BY dans les points du graphique. */
    private static void fill(Map<YearMonth, InteractionChartResponse> byMonth, List<Object[]> rows,
                             ObjLongConsumer<InteractionChartResponse> setter) {
        for (Object[] row : rows) {
            YearMonth ym = YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
            InteractionChartResponse point = byMonth.get(ym);
            if (point != null) {
                setter.accept(point, ((Number) row[2]).longValue());
            }
        }
    }
}
