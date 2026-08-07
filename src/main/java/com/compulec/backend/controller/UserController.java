package com.compulec.backend.controller;

import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.UserResponse;
import com.compulec.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la gestion des utilisateurs.
 * Toutes les routes nécessitent une authentification JWT.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Utilisateurs", description = "Endpoints de gestion des utilisateurs")
public class UserController {

    private final UserService userService;

    /**
     * Récupère le profil de l'utilisateur connecté.
     * GET /api/users/me
     */
    @GetMapping("/me")
    @Operation(summary = "Mon profil", description = "Récupérer les informations de l'utilisateur connecté")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserResponse user = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Récupère tous les utilisateurs (Admin seulement).
     * GET /api/users?page=0&size=10&sort=nom
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Liste des utilisateurs", description = "Récupérer tous les utilisateurs (Admin uniquement)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "nom") String sort) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sort));
        Page<UserResponse> users = userService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * Récupère un utilisateur par son ID (Admin seulement).
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Détails d'un utilisateur", description = "Récupérer un utilisateur par son ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Désactive un utilisateur (Admin seulement).
     * DELETE /api/users/{id}/deactivate
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver un utilisateur", description = "Désactiver (soft delete) un utilisateur")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Utilisateur désactivé avec succès"));
    }

    /**
     * Supprime définitivement un utilisateur (Admin seulement).
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprimer un utilisateur", description = "Suppression définitive d'un utilisateur")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Utilisateur supprimé avec succès"));
    }
}
