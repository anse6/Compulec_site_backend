package com.compulec.backend.service;

import com.compulec.backend.dto.response.MfaSetupResponse;
import com.compulec.backend.entity.MfaBackupCode;
import com.compulec.backend.entity.User;
import com.compulec.backend.repository.MfaBackupCodeRepository;
import com.compulec.backend.repository.RefreshTokenRepository;
import com.compulec.backend.repository.UserRepository;
import com.compulec.backend.security.SecretCipher;
import com.compulec.backend.security.SecureTokenGenerator;
import com.compulec.backend.security.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Double authentification facultative (TOTP + codes de secours), exigée à chaque connexion une fois activée.
 *
 * - Le secret est généré côté serveur, chiffré en base (AES-GCM) et confirmé par un premier code.
 * - Un code TOTP ne peut servir qu'une fois (anti-rejeu par période).
 * - 10 codes de secours à usage unique, stockés hachés, sont donnés à l'activation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService {

    public static final int BACKUP_CODE_COUNT = 10;
    private static final String BACKUP_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final MfaBackupCodeRepository backupCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TotpService totpService;
    private final SecretCipher secretCipher;
    private final SecureTokenGenerator tokenGenerator;
    private final EmailService emailService;

    /**
     * Démarre (ou redémarre) l'enrôlement : nouveau secret en attente de confirmation.
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public MfaSetupResponse startSetup(User user) {
        if (user.isMfaEnabled()) {
            throw new IllegalArgumentException("La double authentification est déjà activée sur ce compte.");
        }
        String secret = totpService.newSecret();
        user.setMfaPendingSecret(secretCipher.encrypt(secret));
        userRepository.save(user);
        return MfaSetupResponse.builder()
                .secret(secret)
                .otpauthUri(totpService.otpAuthUri(secret, user.getEmail()))
                .build();
    }

    /**
     * Confirme l'enrôlement avec un premier code de l'application.
     *
     * @return les codes de secours en clair (affichés une seule fois)
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public List<String> confirmSetup(User user, String code) {
        if (user.getMfaPendingSecret() == null) {
            throw new IllegalArgumentException("Aucune activation de la double authentification en cours. Recommencez.");
        }
        String secret = secretCipher.decrypt(user.getMfaPendingSecret());
        long step = totpService.verify(secret, normalize(code), null);
        if (step < 0) {
            throw new IllegalArgumentException("Code incorrect. Vérifiez l'heure de votre téléphone et réessayez.");
        }

        user.setMfaSecret(user.getMfaPendingSecret());
        user.setMfaPendingSecret(null);
        user.setMfaEnabled(true);
        user.setMfaLastUsedStep(step);
        userRepository.save(user);

        List<String> codes = replaceBackupCodes(user);
        log.info("Double authentification activée pour l'utilisateur {}", user.getId());
        try {
            emailService.sendMfaEnabledEmail(user);
        } catch (Exception e) {
            log.error("Échec de l'envoi de l'alerte d'activation 2FA à {} : {}", user.getId(), e.getMessage());
        }
        return codes;
    }

    /**
     * Vérifie un code TOTP ou un code de secours. N'enregistre pas l'échec (géré par l'appelant).
     */
    @Transactional
    public boolean verify(User user, String rawCode) {
        if (!user.isMfaEnabled() || user.getMfaSecret() == null) {
            return false;
        }
        String code = normalize(rawCode);

        if (code.matches("^[0-9]{6}$")) {
            long step = totpService.verify(secretCipher.decrypt(user.getMfaSecret()), code, user.getMfaLastUsedStep());
            if (step < 0) {
                return false;
            }
            user.setMfaLastUsedStep(step);
            userRepository.save(user);
            return true;
        }

        return backupCodeRepository
                .findByUserAndCodeHashAndUsedAtIsNull(user, tokenGenerator.sha256(code))
                .map(backup -> {
                    backup.setUsedAt(LocalDateTime.now());
                    backupCodeRepository.save(backup);
                    long remaining = backupCodeRepository.countByUserAndUsedAtIsNull(user);
                    log.warn("Code de secours utilisé par l'utilisateur {} ({} restant(s))", user.getId(), remaining);
                    try {
                        emailService.sendBackupCodeUsedEmail(user, remaining);
                    } catch (Exception e) {
                        log.error("Échec de l'alerte code de secours pour {} : {}", user.getId(), e.getMessage());
                    }
                    return true;
                })
                .orElse(false);
    }

    /**
     * Génère de nouveaux codes de secours (les anciens deviennent inutilisables).
     * Exige un code valide de l'application.
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public List<String> regenerateBackupCodes(User user, String code) {
        if (!verify(user, code)) {
            throw new IllegalArgumentException("Code de double authentification incorrect.");
        }
        return replaceBackupCodes(user);
    }

    public long remainingBackupCodes(User user) {
        return backupCodeRepository.countByUserAndUsedAtIsNull(user);
    }

    /**
     * Réinitialisation par un administrateur (téléphone perdu) :
     * la 2FA est supprimée, toutes les sessions sont fermées et
     * l'utilisateur devra la reconfigurer à sa prochaine connexion.
     */
    @Transactional
    public void reset(User user) {
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaPendingSecret(null);
        user.setMfaLastUsedStep(null);
        user.incrementTokenVersion();
        userRepository.save(user);
        backupCodeRepository.deleteAllByUser(user);
        refreshTokenRepository.revokeAllUserTokens(user);
        log.warn("Double authentification réinitialisée par un administrateur pour l'utilisateur {}", user.getId());
        try {
            emailService.sendMfaResetEmail(user);
        } catch (Exception e) {
            log.error("Échec de l'alerte de réinitialisation 2FA pour {} : {}", user.getId(), e.getMessage());
        }
    }

    private List<String> replaceBackupCodes(User user) {
        backupCodeRepository.deleteAllByUser(user);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = randomBackupCode();
            codes.add(code);
            backupCodeRepository.save(MfaBackupCode.builder()
                    .user(user)
                    .codeHash(tokenGenerator.sha256(normalize(code)))
                    .build());
        }
        return codes;
    }

    /** Format XXXX-XXXX, sans caractères ambigus (0/O, 1/I/L). */
    private static String randomBackupCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(BACKUP_ALPHABET.charAt(RANDOM.nextInt(BACKUP_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Ignore espaces et casse ; les codes de secours sont comparés sans tiret. */
    static String normalize(String code) {
        if (code == null) {
            return "";
        }
        String c = code.trim().replace(" ", "").toUpperCase(Locale.ROOT);
        return c.matches("^[0-9]{6}$") ? c : c.replace("-", "");
    }
}
