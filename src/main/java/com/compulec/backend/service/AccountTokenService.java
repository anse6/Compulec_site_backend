package com.compulec.backend.service;

import com.compulec.backend.entity.AccountToken;
import com.compulec.backend.entity.AccountTokenType;
import com.compulec.backend.entity.User;
import com.compulec.backend.repository.AccountTokenRepository;
import com.compulec.backend.security.SecureTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Émission et vérification des jetons de compte à usage unique.
 * Les secrets ne sont renvoyés qu'à l'émission et ne sont jamais stockés en clair.
 */
@Service
@RequiredArgsConstructor
public class AccountTokenService {

    private final AccountTokenRepository tokenRepository;
    private final SecureTokenGenerator generator;
    private final PasswordEncoder passwordEncoder;

    /** Secrets générés pour un jeton : à transmettre puis oublier. */
    public record IssuedToken(String token, String code, LocalDateTime expiresAt) {
    }

    /**
     * Crée un nouveau jeton et invalide les précédents du même type.
     *
     * @param withCode true pour générer en plus un code à 6 chiffres (second canal)
     */
    @Transactional
    public IssuedToken issue(User user, AccountTokenType type, Duration validity, boolean withCode) {
        tokenRepository.revokeAll(user, type);

        String token = generator.newToken();
        String code = withCode ? generator.newCode() : null;
        LocalDateTime expiresAt = LocalDateTime.now().plus(validity);

        tokenRepository.save(AccountToken.builder()
                .user(user)
                .type(type)
                .tokenHash(generator.sha256(token))
                .codeHash(code != null ? passwordEncoder.encode(code) : null)
                .expiresAt(expiresAt)
                .build());

        return new IssuedToken(token, code, expiresAt);
    }

    /**
     * Retrouve un jeton encore utilisable (jamais utilisé, non expiré).
     *
     * Volontairement non transactionnelle : l'exception levée ne doit pas marquer
     * la transaction appelante « rollback-only » (le compteur d'essais doit être conservé).
     *
     * @throws IllegalArgumentException si le jeton est inconnu, utilisé ou expiré
     */
    public AccountToken findUsable(String token, AccountTokenType type, String errorMessage) {
        return tokenRepository.findByTokenHashAndType(generator.sha256(token), type)
                .filter(AccountToken::isUsable)
                .orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    /** true si un jeton a déjà été émis récemment (protection contre l'envoi massif d'e-mails). */
    public boolean issuedRecently(User user, AccountTokenType type, Duration window) {
        return tokenRepository.existsByUserAndTypeAndUsedAtIsNullAndCreatedAtAfter(
                user, type, LocalDateTime.now().minus(window));
    }

    public boolean matchesCode(AccountToken token, String code) {
        return token.getCodeHash() != null && passwordEncoder.matches(code, token.getCodeHash());
    }

    @Transactional
    public void markUsed(AccountToken token) {
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
    }

    @Transactional
    public void save(AccountToken token) {
        tokenRepository.save(token);
    }
}
