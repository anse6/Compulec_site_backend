package com.compulec.backend.repository;

import com.compulec.backend.entity.Projet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository JPA pour l'entité Projet.
 */
@Repository
public interface ProjetRepository extends JpaRepository<Projet, Long> {

    /**
     * Récupère tous les projets publiés ou non.
     */
    Page<Projet> findByPublie(boolean publie, Pageable pageable);
}
