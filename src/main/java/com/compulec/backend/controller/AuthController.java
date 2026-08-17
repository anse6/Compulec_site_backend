package com.compulec.backend.controller;

import com.compulec.backend.dto.request.ForgotPasswordRequest;
import com.compulec.backend.dto.request.LoginRequest;
import com.compulec.backend.dto.request.RegisterRequest;
import com.compulec.backend.dto.request.ResetPasswordRequest;
import com.compulec.backend.dto.request.VerifyCodeRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.AuthResponse;
import com.compulec.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour l'authentification (Inscription Admin, Connexion, Mot de passe oublié).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Endpoints d'inscription Administrateur, connexion, et gestion du mot de passe oublié")
public class AuthController {

    private final AuthService authService;

    /**
     * Inscription d'un Administrateur.
     * POST /api/auth/register
     */
    @PostMapping("/register")
    @Operation(summary = "Inscription Administrateur", description = "Créer un nouveau compte Administrateur (avec envoi d'e-mail de bienvenue)")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Compte Administrateur créé avec succès ! Un e-mail de bienvenue a été envoyé."));
    }

    /**
     * Connexion d'un Administrateur ou Manager.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    @Operation(summary = "Connexion", description = "Se connecter avec email et mot de passe (vérifie que le compte est actif)")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Connexion réussie !"));
    }

    /**
     * Mot de passe oublié - Étape 1 : Saisir l'email et envoyer le code.
     * POST /api/auth/forgot-password
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Mot de passe oublié - Étape 1", description = "Génère et envoie un code de vérification à 6 chiffres par e-mail")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Un code de réinitialisation vous a été envoyé par e-mail."));
    }

    /**
     * Mot de passe oublié - Étape 2 : Confirmer le code de vérification.
     * POST /api/auth/verify-reset-code
     */
    @PostMapping("/verify-reset-code")
    @Operation(summary = "Mot de passe oublié - Étape 2", description = "Vérifie la validité du code de réinitialisation reçu")
    public ResponseEntity<ApiResponse<Void>> verifyResetCode(
            @Valid @RequestBody VerifyCodeRequest request) {
        authService.verifyResetCode(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Code de vérification validé. Vous pouvez réinitialiser votre mot de passe."));
    }

    /**
     * Mot de passe oublié - Étape 3 : Réinitialiser le mot de passe.
     * POST /api/auth/reset-password
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Mot de passe oublié - Étape 3", description = "Réinitialise le mot de passe après validation du code")
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
    @Operation(summary = "Renouveler le token", description = "Obtenir un nouvel access token via le refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestParam String refreshToken) {
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(response, "Token renouvelé avec succès"));
    }

    /**
     * Déconnexion de l'utilisateur.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    @Operation(summary = "Déconnexion", description = "Révoquer tous les tokens de l'utilisateur connecté")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "Déconnexion réussie"));
    }
}
