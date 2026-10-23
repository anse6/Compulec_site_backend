package com.compulec.backend.repository;

import com.compulec.backend.entity.ChatMessage;
import com.compulec.backend.entity.ChatSession;
import com.compulec.backend.entity.ChatSessionStatus;
import com.compulec.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository JPA pour l'entité ChatMessage.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Récupère tous les messages d'une session, triés par date de création (ASC).
     * Utilisé pour afficher l'historique de la conversation dans l'ordre chronologique.
     */
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);

    /** Purge : messages des conversations terminées depuis avant {@code cutoff}. */
    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.session.id IN (SELECT s.id FROM ChatSession s "
            + "WHERE s.status = :status AND COALESCE(s.lastActivityAt, s.createdAt) < :cutoff)")
    void deleteForClosedSessionsBefore(ChatSessionStatus status, LocalDateTime cutoff);

    /** Avant la suppression d'un compte : ses réponses restent, sans auteur. */
    @Modifying
    @Query("UPDATE ChatMessage m SET m.author = NULL WHERE m.author = :user")
    void clearAuthor(User user);
}
