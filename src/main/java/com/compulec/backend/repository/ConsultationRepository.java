package com.compulec.backend.repository;

import com.compulec.backend.entity.Consultation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour l'entité Consultation.
 */
@Repository
public interface ConsultationRepository extends JpaRepository<Consultation, Long> {

    /**
     * Trouver les consultations selon leur statut de lecture (lu ou non lu)
     */
    Page<Consultation> findByLu(boolean lu, Pageable pageable);

    /**
     * Compter le nombre de consultations selon leur statut de lecture
     */
    long countByLu(boolean lu);
}
