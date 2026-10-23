package com.compulec.backend.repository;

import com.compulec.backend.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository JPA pour l'entité Contact.
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    /**
     * Récupère les messages non lus.
     */
    Page<Contact> findByLu(boolean lu, Pageable pageable);

    /**
     * Compte le nombre de messages non lus.
     */
    long countByLu(boolean lu);

    /** Marque tous les messages non lus comme lus (notifications « Tout marquer comme lu »). */
    @Modifying
    @Query("UPDATE Contact c SET c.lu = true, c.updatedAt = :now WHERE c.lu = false")
    int markAllAsRead(LocalDateTime now);

    /** Tableau de bord : nombre de messages créés par mois depuis {@code since} → [année, mois, nombre]. */
    @Query("SELECT YEAR(x.createdAt), MONTH(x.createdAt), COUNT(x) FROM Contact x "
            + "WHERE x.createdAt >= :since GROUP BY YEAR(x.createdAt), MONTH(x.createdAt)")
    List<Object[]> countCreatedPerMonthSince(LocalDateTime since);
}
