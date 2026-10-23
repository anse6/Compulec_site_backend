package com.compulec.backend.repository;

import com.compulec.backend.entity.ChatSession;
import com.compulec.backend.entity.ChatSessionStatus;
import com.compulec.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA pour l'entité ChatSession.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /**
     * Recherche une session par son token UUID unique.
     */
    Optional<ChatSession> findBySessionToken(String sessionToken);

    /**
     * Récupère toutes les sessions ayant un statut donné.
     * Utilisé par le tableau de bord et les notifications.
     */
    List<ChatSession> findByStatusOrderByCreatedAtDesc(ChatSessionStatus status);

    long countByStatusIn(Collection<ChatSessionStatus> statuses);

    /**
     * Tableau de bord : conversations des statuts donnés, les plus récemment actives d'abord,
     * avec le conseiller assigné chargé dans la même requête (pas de N+1).
     */
    @Query("SELECT s FROM ChatSession s LEFT JOIN FETCH s.assignedAdmin WHERE s.status IN :statuses "
            + "ORDER BY COALESCE(s.lastActivityAt, s.createdAt) DESC")
    List<ChatSession> findRecentByStatusIn(Collection<ChatSessionStatus> statuses, Pageable pageable);

    /** Tableau de bord : nombre de conversations créés par mois depuis {@code since} → [année, mois, nombre]. */
    @Query("SELECT YEAR(x.createdAt), MONTH(x.createdAt), COUNT(x) FROM ChatSession x "
            + "WHERE x.createdAt >= :since GROUP BY YEAR(x.createdAt), MONTH(x.createdAt)")
    List<Object[]> countCreatedPerMonthSince(LocalDateTime since);

    /**
     * Conversations d'un statut, triées par dernière activité, avec recherche facultative
     * sur le nom, l'email ou le numéro de session (search = "%terme%" en minuscules, ou null).
     */
    @Query("SELECT s FROM ChatSession s WHERE s.status = :status AND (:search IS NULL "
            + "OR LOWER(s.visitorName) LIKE :search "
            + "OR LOWER(COALESCE(s.visitorEmail, '')) LIKE :search "
            + "OR LOWER(s.sessionToken) LIKE :search) "
            + "ORDER BY COALESCE(s.lastActivityAt, s.createdAt) DESC")
    Page<ChatSession> search(ChatSessionStatus status, String search, Pageable pageable);

    /** Nombre de conversations par statut : [statut, nombre]. */
    @Query("SELECT s.status, COUNT(s) FROM ChatSession s GROUP BY s.status")
    List<Object[]> countByStatus();

    /** Total des messages visiteurs non lus dans les conversations qui attendent l'équipe. */
    @Query("SELECT COALESCE(SUM(s.unreadCount), 0) FROM ChatSession s WHERE s.status IN :statuses")
    long sumUnread(List<ChatSessionStatus> statuses);

    /**
     * Changement de statut atomique : ne réussit que si la conversation est encore dans l'état {@code from}.
     * Deux clics simultanés ne peuvent donc pas déclencher deux fois la même transition.
     *
     * @return 1 si la transition a eu lieu, 0 si un autre appel l'a déjà faite
     */
    @Modifying
    @Query("UPDATE ChatSession s SET s.status = :to, "
            + "s.unreadCount = CASE WHEN s.unreadCount < 1 THEN 1 ELSE s.unreadCount END "
            + "WHERE s.id = :id AND s.status = :from")
    int transition(Long id, ChatSessionStatus from, ChatSessionStatus to);

    /** Demandes de conseiller en attente depuis avant {@code cutoff}, pas encore relancées. */
    @Query("SELECT s FROM ChatSession s WHERE s.status = :status AND s.reminderSentAt IS NULL "
            + "AND COALESCE(s.lastActivityAt, s.createdAt) < :cutoff")
    List<ChatSession> findWaitingWithoutReminder(ChatSessionStatus status, LocalDateTime cutoff);

    /** Conversations sans activité depuis {@code cutoff}. */
    @Query("SELECT s FROM ChatSession s WHERE s.status IN :statuses AND COALESCE(s.lastActivityAt, s.createdAt) < :cutoff")
    List<ChatSession> findInactive(List<ChatSessionStatus> statuses, LocalDateTime cutoff);

    /** Purge des conversations terminées depuis avant {@code cutoff} (leurs messages doivent être supprimés avant). */
    @Modifying
    @Query("DELETE FROM ChatSession s WHERE s.status = :status AND COALESCE(s.lastActivityAt, s.createdAt) < :cutoff")
    int deleteClosedBefore(ChatSessionStatus status, LocalDateTime cutoff);

    /** Avant la suppression d'un compte : détacher ses conversations. */
    @Modifying
    @Query("UPDATE ChatSession s SET s.assignedAdmin = NULL WHERE s.assignedAdmin = :user")
    void clearAssignedAdmin(User user);
}
