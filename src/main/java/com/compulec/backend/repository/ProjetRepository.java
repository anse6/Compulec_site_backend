package com.compulec.backend.repository;

import com.compulec.backend.entity.Projet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository JPA pour l'entité Projet.
 */
@Repository
public interface ProjetRepository extends JpaRepository<Projet, Long> {

    /**
     * Récupère tous les projets publiés ou non.
     */
    Page<Projet> findByPublie(boolean publie, Pageable pageable);

    long countByPublieTrue();

    /** Tableau de bord : nombre de projets créés par mois depuis {@code since} → [année, mois, nombre]. */
    @Query("SELECT YEAR(x.createdAt), MONTH(x.createdAt), COUNT(x) FROM Projet x "
            + "WHERE x.createdAt >= :since GROUP BY YEAR(x.createdAt), MONTH(x.createdAt)")
    List<Object[]> countCreatedPerMonthSince(LocalDateTime since);
}
