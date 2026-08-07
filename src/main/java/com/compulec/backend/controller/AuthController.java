package com.compulec.backend.controller;

import com.compulec.backend.dto.request.LoginRequest;
import com.compulec.backend.dto.request.RegisterRequest;
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
 * Contrôleur REST pour la gestion de l'authentification.
 * Endpoints : inscription, connexion, refresh token, déconnexion.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Endpoints de gestion de l'authentification (JWT)")
public class AuthController {

    private final AuthService authService;

    /**
     * Inscription d'un nouvel utilisateur.
     * POST /api/auth/register
     */
    @PostMapping("/register")
    @Operation(summary = "Inscription", description = "Créer un nouveau compte utilisateur")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Inscription réussie ! Bienvenue sur Compulec."));
    }

    /**
     * Connexion d'un utilisateur existant.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    @Operation(summary = "Connexion", description = "Se connecter avec email et mot de passe")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Connexion réussie !"));
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
