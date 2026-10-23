package com.compulec.backend.repository;

import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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

    /**
     * Récupère tous les utilisateurs ayant un rôle spécifique (ex: MANAGER).
     */
    Page<User> findByRole(Role role, Pageable pageable);

    boolean existsByRole(Role role);

    /** Administrateurs actifs, utilisés pour les notifications de sécurité et le chat. */
    List<User> findByRoleAndActifTrue(Role role);

    long countByRoleAndActifTrue(Role role);

    /** Comptes actifs ET activés (même règle que {@link User#isEnabled()}) ayant l'un des rôles donnés. */
    long countByRoleInAndActifTrueAndActivatedAtIsNotNull(Collection<Role> roles);

    /**
     * Migration : les comptes créés avant le système d'invitation sont considérés comme activés.
     * Un compte qui possède une invitation (même expirée) n'est pas concerné.
     */
    @Modifying
    @Query("UPDATE User u SET u.activatedAt = u.createdAt WHERE u.activatedAt IS NULL AND NOT EXISTS " +
           "(SELECT t.id FROM AccountToken t WHERE t.user = u AND t.type = com.compulec.backend.entity.AccountTokenType.INVITATION)")
    int markLegacyUsersActivated();
}
