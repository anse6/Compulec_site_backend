package com.compulec.backend.repository;

import com.compulec.backend.entity.AccountToken;
import com.compulec.backend.entity.AccountTokenType;
import com.compulec.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository JPA pour les jetons de compte à usage unique.
 */
@Repository
public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    Optional<AccountToken> findByTokenHashAndType(String tokenHash, AccountTokenType type);

    boolean existsByUserAndTypeAndUsedAtIsNullAndCreatedAtAfter(User user, AccountTokenType type, LocalDateTime after);

    /** Invalide tous les jetons encore ouverts d'un utilisateur pour un type donné. */
    @Modifying
    @Query("UPDATE AccountToken t SET t.usedAt = CURRENT_TIMESTAMP " +
           "WHERE t.user = :user AND t.type = :type AND t.usedAt IS NULL")
    void revokeAll(User user, AccountTokenType type);

    @Modifying
    @Query("DELETE FROM AccountToken t WHERE t.user = :user")
    void deleteAllByUser(User user);
}
