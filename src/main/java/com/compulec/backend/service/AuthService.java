package com.compulec.backend.service;

import com.compulec.backend.dto.request.ForgotPasswordRequest;
import com.compulec.backend.dto.request.LoginRequest;
import com.compulec.backend.dto.request.ResetPasswordRequest;
import com.compulec.backend.dto.response.AuthResponse;
import com.compulec.backend.dto.response.MfaSetupResponse;
import com.compulec.backend.dto.response.UserResponse;
import com.compulec.backend.entity.AccountToken;
import com.compulec.backend.entity.AccountTokenType;
import com.compulec.backend.entity.RefreshToken;
import com.compulec.backend.entity.User;
import com.compulec.backend.exception.SessionExpiredException;
import com.compulec.backend.repository.RefreshTokenRepository;
import com.compulec.backend.repository.UserRepository;
import com.compulec.backend.security.JwtService;
import com.compulec.backend.security.PasswordPolicy;
import com.compulec.backend.security.SecureTokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de gestion de l'authentification des utilisateurs.
 * Connexion (mot de passe, puis code 2FA si l'utilisateur l'a activée), verrouillage anti force brute,
 * refresh tokens rotatifs en cookie HttpOnly et réinitialisation du mot de passe par lien.
 *
 * Il n'existe plus d'inscription publique : le premier administrateur est créé
 * au démarrage (voir AccountBootstrap) et les managers sont invités par un admin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    public static final Duration RESET_VALIDITY = Duration.ofMinutes(30);
    private static final Duration RESET_RESEND_DELAY = Duration.ofMinutes(2);

    private static final Duration REFRESH_REUSE_GRACE = Duration.ofSeconds(30);

    private static final String BAD_CREDENTIALS = "Email ou mot de passe incorrect";
    private static final String SESSION_EXPIRED = "Session expirée. Veuillez vous reconnecter.";
    private static final String MFA_EXPIRED = "Étape de connexion expirée. Veuillez vous reconnecter.";

    /** Session ouverte : corps JSON + refresh token à placer dans le cookie HttpOnly. */
    public record Session(AuthResponse response, String refreshToken) {
    }

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AccountTokenService accountTokenService;
    private final MfaService mfaService;
    private final SecureTokenGenerator tokenGenerator;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Hash factice : la vérification prend le même temps que l'email existe ou non. */
    private volatile String dummyHash;

    /**
     * Connecte un utilisateur (Admin ou Manager).
     *
     * - Message identique que l'email existe ou non (pas d'énumération des comptes).
     * - 5 échecs consécutifs → compte verrouillé 15 minutes + e-mail d'alerte.
     * - L'état du compte (désactivé, invitation en attente) n'est révélé qu'après un mot de passe correct.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public Session login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().trim()).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.getPassword(), dummyHash());
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
            throw new LockedException("Trop de tentatives de connexion. Réessayez dans " + minutes + " minute(s).");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            registerFailedLogin(user);
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }

        if (user.getActivatedAt() == null) {
            throw new DisabledException("Votre compte n'est pas encore activé. "
                    + "Utilisez le lien d'invitation reçu par e-mail et le code fourni par votre administrateur.");
        }
        if (!user.isActif()) {
            throw new DisabledException("Votre compte a été désactivé. Veuillez contacter un administrateur.");
        }

        if (user.isMfaEnabled()) {
            // 2FA activée : étape du code obligatoire. Le compteur d'échecs n'est PAS remis
            // à zéro ici, sinon un attaquant connaissant le mot de passe pourrait deviner le code
            // à l'infini en se reconnectant entre chaque essai.
            return new Session(AuthResponse.builder()
                    .mfaRequired(true)
                    .mfaToken(jwtService.generateMfaToken(user, false))
                    .build(), null);
        }

        // 2FA facultative et pas encore activée : la session est ouverte tout de suite,
        // et on propose d'activer la 2FA (jeton d'activation valable 5 minutes).
        Session session = completeLogin(user, null);
        session.response().setMfaSetupAvailable(true);
        session.response().setMfaToken(jwtService.generateMfaToken(user, true));
        return session;
    }

    /**
     * Étape 2FA de la connexion : code de l'application ou code de secours.
     */
    @Transactional(noRollbackFor = {AuthenticationException.class, SessionExpiredException.class})
    public Session verifyMfa(String mfaToken, String code) {
        User user = userFromMfaToken(mfaToken, false);
        if (!mfaService.verify(user, code)) {
            registerFailedLogin(user);
            throw new BadCredentialsException("Code de double authentification incorrect");
        }
        return completeLogin(user, null);
    }

    /**
     * Activation facultative de la 2FA proposée après la connexion : génère le secret à scanner.
     */
    @Transactional(noRollbackFor = SessionExpiredException.class)
    public MfaSetupResponse startMfaSetup(String mfaToken) {
        return mfaService.startSetup(userFromMfaToken(mfaToken, true));
    }

    /**
     * Confirme l'activation de la 2FA et ouvre la session (avec les codes de secours).
     */
    @Transactional(noRollbackFor = {AuthenticationException.class, IllegalArgumentException.class, SessionExpiredException.class})
    public Session confirmMfaSetup(String mfaToken, String code) {
        User user = userFromMfaToken(mfaToken, true);
        List<String> backupCodes;
        try {
            backupCodes = mfaService.confirmSetup(user, code);
        } catch (IllegalArgumentException e) {
            registerFailedLogin(user);
            throw e;
        }
        return completeLogin(user, backupCodes);
    }

    /**
     * Mot de passe oublié : envoie un lien à usage unique.
     * La réponse est toujours la même (le compte existe ou non), pour ne rien révéler.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail().trim()).orElse(null);

        if (user == null || !user.isEnabled()) {
            log.info("Demande de réinitialisation ignorée (compte inexistant, inactif ou non activé)");
            return;
        }
        if (accountTokenService.issuedRecently(user, AccountTokenType.PASSWORD_RESET, RESET_RESEND_DELAY)) {
            log.info("Demande de réinitialisation ignorée pour {} : lien envoyé il y a moins de 2 minutes", user.getId());
            return;
        }

        AccountTokenService.IssuedToken issued = accountTokenService.issue(
                user, AccountTokenType.PASSWORD_RESET, RESET_VALIDITY, false);
        String link = frontendUrl + "/reset-password#" + issued.token();

        try {
            emailService.sendPasswordResetLinkEmail(user, link, RESET_VALIDITY);
            log.info("Lien de réinitialisation envoyé à l'utilisateur {}", user.getId());
        } catch (Exception e) {
            log.error("Échec de l'envoi du lien de réinitialisation à {} : {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Réinitialise le mot de passe à partir du lien reçu par e-mail.
     * Toutes les sessions existantes sont fermées.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        AccountToken resetToken = accountTokenService.findUsable(request.getToken(), AccountTokenType.PASSWORD_RESET,
                "Ce lien de réinitialisation est invalide, a déjà été utilisé ou a expiré. Faites une nouvelle demande.");
        User user = resetToken.getUser();

        PasswordPolicy.validate(request.getNewPassword(), request.getConfirmNewPassword(), user);

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.incrementTokenVersion();
        userRepository.save(user);

        accountTokenService.markUsed(resetToken);
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Mot de passe réinitialisé pour l'utilisateur {}", user.getId());

        try {
            emailService.sendPasswordChangedEmail(user);
        } catch (Exception e) {
            log.error("Échec de l'envoi de l'alerte de changement de mot de passe à {} : {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Renouvelle l'access token à partir du refresh token (cookie HttpOnly).
     *
     * Rotation : le refresh token utilisé est remplacé par un nouveau.
     * Si un refresh token déjà remplacé est présenté plus de 30 secondes après,
     * il a probablement été volé : toutes les sessions de l'utilisateur sont révoquées.
     */
    @Transactional(noRollbackFor = {SessionExpiredException.class, AuthenticationException.class})
    public Session refreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new SessionExpiredException(SESSION_EXPIRED);
        }
        RefreshToken stored = refreshTokenRepository.findByToken(tokenGenerator.sha256(rawRefreshToken))
                .orElseThrow(() -> new SessionExpiredException(SESSION_EXPIRED));
        User user = stored.getUser();

        if (stored.isRevoked()) {
            boolean concurrentRefresh = stored.getReplacedAt() != null
                    && stored.getReplacedAt().isAfter(LocalDateTime.now().minus(REFRESH_REUSE_GRACE));
            if (!concurrentRefresh) {
                refreshTokenRepository.revokeAllUserTokens(user);
                log.warn("Réutilisation d'un refresh token révoqué pour l'utilisateur {} : toutes ses sessions sont fermées",
                        user.getId());
            }
            throw new SessionExpiredException(SESSION_EXPIRED);
        }
        if (!stored.isValid()) {
            throw new SessionExpiredException(SESSION_EXPIRED);
        }
        if (!user.isEnabled()) {
            refreshTokenRepository.revokeAllUserTokens(user);
            throw new DisabledException("Votre compte a été désactivé. Veuillez contacter un administrateur.");
        }

        stored.setRevoked(true);
        stored.setReplacedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        String newRefreshToken = saveRefreshToken(user);
        return new Session(buildAuthResponse(jwtService.generateToken(user), user, null), newRefreshToken);
    }

    /**
     * Déconnexion : révoque la session identifiée par le cookie (et celles de l'utilisateur connecté).
     */
    @Transactional
    public void logout(String rawRefreshToken, String authenticatedEmail) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenRepository.findByToken(tokenGenerator.sha256(rawRefreshToken))
                    .ifPresent(token -> refreshTokenRepository.revokeAllUserTokens(token.getUser()));
        }
        if (authenticatedEmail != null) {
            userRepository.findByEmail(authenticatedEmail).ifPresent(refreshTokenRepository::revokeAllUserTokens);
        }
    }

    /**
     * Ferme toutes les sessions existantes de l'utilisateur et en ouvre une nouvelle.
     */
    @Transactional
    public Session startSession(User user) {
        refreshTokenRepository.revokeAllUserTokens(user);
        String refreshToken = saveRefreshToken(user);
        return new Session(buildAuthResponse(jwtService.generateToken(user), user, null), refreshToken);
    }

    // ===== Méthodes privées =====

    private Session completeLogin(User user, List<String> backupCodes) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        log.info("Connexion réussie (2FA) pour l'utilisateur {}", user.getId());

        Session session = startSession(user);
        session.response().setBackupCodes(backupCodes);
        return session;
    }

    /**
     * Retrouve l'utilisateur d'un jeton 2FA et revérifie l'état du compte
     * (désactivé, verrouillé ou mot de passe changé entre-temps → refus).
     */
    private User userFromMfaToken(String mfaToken, boolean setup) {
        String email = mfaToken == null ? null : jwtService.extractMfaSubject(mfaToken, setup);
        if (email == null) {
            throw new SessionExpiredException(MFA_EXPIRED);
        }
        User user = userRepository.findByEmail(email).orElseThrow(() -> new SessionExpiredException(MFA_EXPIRED));
        Integer version = jwtService.extractTokenVersion(mfaToken);
        if (version == null || version != user.getTokenVersion() || !user.isEnabled()) {
            throw new SessionExpiredException(MFA_EXPIRED);
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new LockedException("Trop de tentatives. Réessayez dans quelques minutes.");
        }
        if (setup == user.isMfaEnabled()) {
            throw new SessionExpiredException(MFA_EXPIRED);
        }
        return user;
    }

    private void registerFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= MAX_FAILED_LOGINS) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(LocalDateTime.now().plus(LOCK_DURATION));
            log.warn("Compte {} verrouillé {} minutes après {} échecs de connexion",
                    user.getId(), LOCK_DURATION.toMinutes(), MAX_FAILED_LOGINS);
            try {
                emailService.sendAccountLockedEmail(user, LOCK_DURATION);
            } catch (Exception e) {
                log.error("Échec de l'envoi de l'alerte de verrouillage à {} : {}", user.getId(), e.getMessage());
            }
        } else {
            user.setFailedLoginAttempts(attempts);
        }
        userRepository.save(user);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("compulec-dummy-password-for-timing");
        }
        return dummyHash;
    }

    /** Refresh token opaque (256 bits) : seule son empreinte est stockée. */
    private String saveRefreshToken(User user) {
        String token = tokenGenerator.newToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenGenerator.sha256(token))
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private AuthResponse buildAuthResponse(String accessToken, User user, List<String> backupCodes) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .backupCodes(backupCodes)
                .expiresIn(jwtExpiration / 1000)
                .user(AuthService.mapToUserResponse(user))
                .build();
    }

    public static UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .email(user.getEmail())
                .phone(user.getPhone())
                .dateNaissance(user.getDateNaissance())
                .fonction(user.getFonction())
                .sexe(user.getSexe())
                .role(user.getRole())
                .actif(user.isActif())
                .pendingInvitation(user.getActivatedAt() == null)
                .activatedAt(user.getActivatedAt())
                .mfaEnabled(user.isMfaEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
