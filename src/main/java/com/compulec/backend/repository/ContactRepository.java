package com.compulec.backend.repository;

import com.compulec.backend.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
