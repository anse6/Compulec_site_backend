package com.compulec.backend.service;

import com.compulec.backend.dto.response.InvitationPreviewResponse;
import com.compulec.backend.dto.response.ManagerCreatedResponse;
import com.compulec.backend.entity.AccountToken;
import com.compulec.backend.entity.AccountTokenType;
import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.User;
import com.compulec.backend.repository.RefreshTokenRepository;
import com.compulec.backend.repository.UserRepository;
import com.compulec.backend.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Invitation des managers par « double canal » :
 *  1. un lien à usage unique (jeton 256 bits) envoyé par e-mail ;
 *  2. un code à 6 chiffres affiché UNE fois à l'admin, qui le transmet de vive voix.
 *
 * Il faut les deux pour activer le compte : voir l'e-mail (ou la notification
 * sur le téléphone) ne suffit pas. Le manager choisit lui-même son mot de passe ;
 * l'admin ne le connaît jamais.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {

    public static final Duration INVITATION_VALIDITY = Duration.ofHours(24);
    public static final int MAX_CODE_ATTEMPTS = 5;

    private static final String INVALID_LINK = "Ce lien d'invitation est invalide, a déjà été utilisé ou a expiré. "
            + "Demandez à votre administrateur de vous renvoyer une invitation.";

    private final AccountTokenService accountTokenService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Crée (ou renouvelle) l'invitation d'un compte non encore activé.
     * L'ancien lien et l'ancien code deviennent immédiatement inutilisables.
     */
    @Transactional
    public ManagerCreatedResponse issueInvitation(User user) {
        if (user.getActivatedAt() != null) {
            throw new IllegalArgumentException("Ce compte est déjà activé : aucune invitation n'est nécessaire.");
        }

        AccountTokenService.IssuedToken issued = accountTokenService.issue(
                user, AccountTokenType.INVITATION, INVITATION_VALIDITY, true);

        // Le jeton est placé dans le fragment (#) : il n'est jamais envoyé au serveur
        // qui héberge le frontend, ni écrit dans ses logs ou dans l'en-tête Referer.
        String link = frontendUrl + "/activate-account#" + issued.token();

        boolean emailSent = true;
        try {
            emailService.sendInvitationEmail(user, link, INVITATION_VALIDITY);
        } catch (Exception e) {
            emailSent = false;
            log.error("Échec de l'envoi de l'invitation à l'utilisateur {} : {}", user.getId(), e.getMessage());
        }

        log.info("Invitation émise pour l'utilisateur {} (expire le {})", user.getId(), issued.expiresAt());
        return ManagerCreatedResponse.builder()
                .user(AuthService.mapToUserResponse(user))
                .activationCode(issued.code())
                .invitationExpiresAt(issued.expiresAt())
                .emailSent(emailSent)
                .build();
    }

    /**
     * Vérifie un lien d'invitation avant d'afficher le formulaire d'activation.
     * Ne révèle que le prénom et un e-mail masqué.
     */
    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String token) {
        User user = accountTokenService.findUsable(token, AccountTokenType.INVITATION, INVALID_LINK).getUser();
        return InvitationPreviewResponse.builder()
                .prenom(user.getPrenom())
                .maskedEmail(maskEmail(user.getEmail()))
                .build();
    }

    /**
     * Active le compte : vérifie le lien, le code (5 essais maximum) et la politique de mot de passe.
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void accept(String token, String code, String password, String confirmPassword) {
        AccountToken invitation = accountTokenService.findUsable(token, AccountTokenType.INVITATION, INVALID_LINK);
        User user = invitation.getUser();

        if (!accountTokenService.matchesCode(invitation, code)) {
            invitation.setAttempts(invitation.getAttempts() + 1);
            int remaining = MAX_CODE_ATTEMPTS - invitation.getAttempts();
            if (remaining <= 0) {
                invitation.setUsedAt(LocalDateTime.now());
                accountTokenService.save(invitation);
                log.warn("Invitation de l'utilisateur {} révoquée après {} codes incorrects",
                        user.getId(), MAX_CODE_ATTEMPTS);
                notifyAdminsOfRevokedInvitation(user);
                throw new IllegalArgumentException("Trop de codes incorrects : cette invitation a été annulée. "
                        + "Demandez à votre administrateur de vous en renvoyer une.");
            }
            accountTokenService.save(invitation);
            throw new IllegalArgumentException("Code d'activation incorrect. Tentatives restantes : " + remaining);
        }

        PasswordPolicy.validate(password, confirmPassword, user);

        user.setPassword(passwordEncoder.encode(password));
        user.setActif(true);
        user.setActivatedAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.incrementTokenVersion();
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(user);

        accountTokenService.markUsed(invitation);
        log.info("Invitation acceptée : compte {} activé", user.getId());

        try {
            emailService.sendAccountActivatedEmail(user);
            userRepository.findByRoleAndActifTrue(Role.ADMIN)
                    .forEach(admin -> emailService.sendAccountActivatedAdminNotice(admin, user));
        } catch (Exception e) {
            log.error("Échec de l'envoi des notifications d'activation pour {} : {}", user.getId(), e.getMessage());
        }
    }

    private void notifyAdminsOfRevokedInvitation(User user) {
        try {
            userRepository.findByRoleAndActifTrue(Role.ADMIN)
                    .forEach(admin -> emailService.sendInvitationRevokedAdminNotice(admin, user));
        } catch (Exception e) {
            log.error("Échec de la notification de révocation pour {} : {}", user.getId(), e.getMessage());
        }
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "•••" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "•••" + email.charAt(at - 1) + email.substring(at);
    }
}
