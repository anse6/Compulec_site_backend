package com.compulec.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Code de secours 2FA à usage unique (en cas de perte du téléphone).
 * Seule l'empreinte SHA-256 est stockée.
 */
@Entity
@Table(name = "mfa_backup_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MfaBackupCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
