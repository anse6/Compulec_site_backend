package com.compulec.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Entité représentant un utilisateur (Admin, Manager, User) de l'application.
 * Implémente UserDetails pour Spring Security.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "prenom", nullable = false, length = 100)
    private String prenom;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    @Column(name = "fonction", nullable = false, length = 100)
    private String fonction;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexe", nullable = false, length = 10)
    private Sexe sexe;

    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private boolean actif = true;

    /** Date d'acceptation de l'invitation. null = invitation en attente (connexion impossible). */
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    /**
     * Version des jetons JWT. Incrémentée à chaque événement de sécurité
     * (désactivation, changement / réinitialisation du mot de passe) :
     * tous les access tokens émis avant deviennent immédiatement invalides.
     */
    @Column(name = "token_version", nullable = false, columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private int tokenVersion = 0;

    /** Échecs de connexion consécutifs (remis à zéro après une connexion réussie). */
    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private int failedLoginAttempts = 0;

    /** Compte verrouillé temporairement jusqu'à cette date après trop d'échecs. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // ===================== Double authentification (TOTP) =====================

    /** 2FA activée (facultative) : si oui, chaque connexion exige un code de l'application. */
    @Column(name = "mfa_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private boolean mfaEnabled = false;

    /** Secret TOTP chiffré (AES-GCM, voir SecretCipher). */
    @Column(name = "mfa_secret", length = 255)
    private String mfaSecret;

    /** Secret en cours d'enrôlement (pas encore confirmé par un premier code). */
    @Column(name = "mfa_pending_secret", length = 255)
    private String mfaPendingSecret;

    /** Dernière période TOTP acceptée : un même code ne peut pas être rejoué. */
    @Column(name = "mfa_last_used_step")
    private Long mfaLastUsedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===================== UserDetails Methods =====================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return actif && activatedAt != null;
    }

    /** Invalide immédiatement tous les access tokens existants de cet utilisateur. */
    public void incrementTokenVersion() {
        tokenVersion++;
    }
}
