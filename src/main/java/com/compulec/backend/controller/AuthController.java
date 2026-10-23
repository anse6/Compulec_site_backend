package com.compulec.backend.controller;

import com.compulec.backend.dto.request.AcceptInvitationRequest;
import com.compulec.backend.dto.request.ForgotPasswordRequest;
import com.compulec.backend.dto.request.LoginRequest;
import com.compulec.backend.dto.request.MfaCodeRequest;
import com.compulec.backend.dto.request.ResetPasswordRequest;
import com.compulec.backend.dto.request.TokenRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.AuthResponse;
import com.compulec.backend.dto.response.InvitationPreviewResponse;
import com.compulec.backend.dto.response.MfaSetupResponse;
import com.compulec.backend.security.RefreshTokenCookie;
import com.compulec.backend.service.AuthService;
import com.compulec.backend.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour l'authentification : connexion en 2 étapes (mot de passe + 2FA),
 * invitations, mot de passe oublié. Le refresh token circule uniquement dans un cookie HttpOnly.
 *
 * Il n'y a volontairement plus d'inscription publique (/auth/register) :
 * le premier administrateur est créé au démarrage et les managers sont invités.
 * Tous les jetons circulent dans le corps des requêtes, jamais dans l'URL.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion, activation de compte par invitation et réinitialisation du mot de passe")
public class AuthController {

    private final AuthService authService;
    private final InvitationService invitationService;
    private final RefreshTokenCookie refreshTokenCookie;

    /**
     * Connexion d'un Administrateur ou Manager.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    @Operation(summary = "Connexion", description = "Vérifie email et mot de passe (verrouillage 15 min après 5 échecs). "
            + "Sans 2FA : session ouverte + proposition facultative d'activer la 2FA. "
            + "Avec 2FA : renvoie un mfa_token pour l'étape du code.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthService.Session session = authService.login(request);
        if (session.refreshToken() == null) {
            return ResponseEntity.ok(ApiResponse.success(session.response(),
                    "Mot de passe correct. Saisissez le code de votre application d'authentification."));
        }
        return withSession(session, "Connexion réussie !");
    }

    /**
     * Connexion – étape 2 : code de l'application ou code de secours.
     * POST /api/auth/mfa/verify
     */
    @PostMapping("/mfa/verify")
    @Operation(summary = "Connexion – étape 2FA", description = "Valide le code TOTP (ou un code de secours) et ouvre la session")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(@Valid @RequestBody MfaCodeRequest request) {
        return withSession(authService.verifyMfa(request.getMfaToken(), request.getCode()), "Connexion réussie !");
    }

    /**
     * Première connexion : génère le QR code de la 2FA.
     * POST /api/auth/mfa/setup
     */
    @PostMapping("/mfa/setup")
    @Operation(summary = "Activer la 2FA – QR code", description = "Génère le secret à scanner avec l'application d'authentification")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> startMfaSetup(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.startMfaSetup(request.getToken())));
    }

    /**
     * Première connexion : confirme la 2FA avec un premier code, ouvre la session et renvoie les codes de secours.
     * POST /api/auth/mfa/setup/confirm
     */
    @PostMapping("/mfa/setup/confirm")
    @Operation(summary = "Activer la 2FA – confirmation", description = "Confirme avec un premier code ; renvoie les 10 codes de secours (une seule fois)")
    public ResponseEntity<ApiResponse<AuthResponse>> confirmMfaSetup(@Valid @RequestBody MfaCodeRequest request) {
        return withSession(authService.confirmMfaSetup(request.getMfaToken(), request.getCode()),
                "Double authentification activée. Conservez vos codes de secours en lieu sûr.");
    }

    /**
     * Vérifie un lien d'invitation avant d'afficher le formulaire d'activation.
     * POST /api/auth/invitations/preview
     */
    @PostMapping("/invitations/preview")
    @Operation(summary = "Vérifier une invitation", description = "Indique si le lien d'invitation est encore valide (prénom et e-mail masqué)")
    public ResponseEntity<ApiResponse<InvitationPreviewResponse>> previewInvitation(
            @Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(invitationService.preview(request.getToken())));
    }

    /**
     * Accepte une invitation : lien + code d'activation + mot de passe choisi par le manager.
     * POST /api/auth/invitations/accept
     */
    @PostMapping("/invitations/accept")
    @Operation(summary = "Activer son compte", description = "Active le compte avec le jeton du lien, le code transmis par l'admin (5 essais) et le nouveau mot de passe")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request) {
        invitationService.accept(request.getToken(), request.getCode(),
                request.getPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Votre compte est activé. Vous pouvez maintenant vous connecter."));
    }

    /**
     * Mot de passe oublié : envoie un lien à usage unique (réponse identique que le compte existe ou non).
     * POST /api/auth/forgot-password
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Mot de passe oublié", description = "Envoie un lien de réinitialisation valable 30 minutes")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null,
                "Si un compte actif correspond à cette adresse, un lien de réinitialisation vient d'y être envoyé."));
    }

    /**
     * Réinitialise le mot de passe avec le jeton du lien reçu par e-mail.
     * POST /api/auth/reset-password
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe", description = "Définit un nouveau mot de passe à partir du lien reçu par e-mail ; toutes les sessions sont fermées")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Votre mot de passe a été réinitialisé avec succès !"));
    }

    /**
     * Renouvellement du token d'accès.
     * POST /api/auth/refresh-token
     */
    @PostMapping("/refresh-token")
    @Operation(summary = "Renouveler le token", description = "Nouvel access token à partir du cookie HttpOnly (rotation du refresh token)")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        return withSession(authService.refreshToken(refreshToken), "Token renouvelé avec succès");
    }

    /**
     * Déconnexion de l'utilisateur.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    @Operation(summary = "Déconnexion", description = "Révoque la session (cookie) et efface le cookie, même si l'access token a expiré")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            @AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(refreshToken, userDetails != null ? userDetails.getUsername() : null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear())
                .body(ApiResponse.success(null, "Déconnexion réussie"));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withSession(AuthService.Session session, String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(session.refreshToken()))
                .body(ApiResponse.success(session.response(), message));
    }
}
