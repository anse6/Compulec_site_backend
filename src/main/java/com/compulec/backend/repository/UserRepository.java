package com.compulec.backend.repository;

import com.compulec.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository JPA pour l'entité User.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Recherche un utilisateur par son email.
     *
     * @param email l'adresse email de l'utilisateur
     * @return un Optional contenant l'utilisateur s'il existe
     */
    Optional<User> findByEmail(String email);

    /**
     * Vérifie si un email existe déjà en base de données.
     *
     * @param email l'adresse email à vérifier
     * @return true si l'email existe, false sinon
     */
    boolean existsByEmail(String email);
}
