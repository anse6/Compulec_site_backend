package com.compulec.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Jeton de compte à usage unique (invitation ou réinitialisation de mot de passe).
 *
 * Aucun secret n'est stocké en clair :
 *  - tokenHash : SHA-256 du jeton envoyé par e-mail (256 bits d'entropie)
 *  - codeHash  : BCrypt du code à 6 chiffres transmis par un second canal (invitation uniquement)
 */
@Entity
@Table(name = "account_tokens", indexes = {
        @Index(name = "idx_account_tokens_hash", columnList = "token_hash", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AccountTokenType type;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "code_hash")
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Un jeton est utilisable s'il n'a jamais servi et n'a pas expiré. */
    public boolean isUsable() {
        return usedAt == null && LocalDateTime.now().isBefore(expiresAt);
    }
}
