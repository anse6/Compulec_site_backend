package com.compulec.backend.controller;

import com.compulec.backend.dto.request.CreateManagerRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.UserResponse;
import com.compulec.backend.entity.Role;
import com.compulec.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la gestion des utilisateurs et des managers ( réservé aux Administrateurs ).
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Gestion Utilisateurs & Managers", description = "Endpoints réservés à l'Administrateur pour gérer les comptes et managers")
public class UserController {

    private final UserService userService;

    /**
     * Récupère le profil de l'utilisateur connecté (Admin ou Manager).
     * GET /api/users/me
     */
    @GetMapping("/me")
    @Operation(summary = "Mon profil", description = "Récupérer les informations de l'utilisateur actuellement connecté")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserResponse user = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Créer un compte Manager (Seul l'ADMIN a le droit, envoie un e-mail de bienvenue).
     * POST /api/users/managers
     */
    @PostMapping("/managers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer un Manager", description = "Permet à l'Administrateur de créer un compte Manager (avec e-mail de bienvenue)")
    public ResponseEntity<ApiResponse<UserResponse>> createManager(
            @Valid @RequestBody CreateManagerRequest request) {
        UserResponse manager = userService.createManager(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(manager, "Compte Manager créé avec succès ! Un e-mail de bienvenue a été envoyé."));
    }

    /**
     * Modifier le rôle d'un utilisateur / manager (Admin seulement).
     * PUT /api/users/{id}/role
     */
    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier le rôle", description = "Permet à l'Administrateur de modifier le rôle d'un utilisateur (ADMIN, MANAGER, USER)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable Long id,
            @RequestParam Role role) {
        UserResponse user = userService.updateUserRole(id, role);
        return ResponseEntity.ok(ApiResponse.success(user, "Rôle de l'utilisateur mis à jour avec succès !"));
    }

    /**
     * Obtenir la liste de tous les Managers (Admin seulement).
     * GET /api/users/managers
     */
    @GetMapping("/managers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Liste des Managers", description = "Récupérer tous les comptes Managers")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getManagers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "nom") String sort) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sort));
        Page<UserResponse> managers = userService.getManagers(pageable);
        return ResponseEntity.ok(ApiResponse.success(managers));
    }

    /**
     * Récupère tous les utilisateurs (Admin seulement).
     * GET /api/users
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Liste de tous les utilisateurs", description = "Récupérer l'ensemble des utilisateurs")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "nom") String sort) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sort));
        Page<UserResponse> users = userService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * Modifier les informations de profil (Admin peut tout modifier, un utilisateur ne peut modifier que lui-même).
     * PUT /api/users/{id}
     */
    @PutMapping("/{id}")
    @Operation(summary = "Modifier les informations de profil", description = "Permet de modifier le nom, prénom, téléphone, date de naissance, fonction, sexe. (Admin ou propriétaire du compte uniquement)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody com.compulec.backend.dto.request.UpdateUserRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        UserResponse currentUser = userService.getUserByEmail(userDetails.getUsername());
        
        // Sécurité : Si l'utilisateur connecté n'est pas ADMIN, il ne peut modifier que son propre ID
        if (!currentUser.getRole().name().equals("ADMIN") && !currentUser.getId().equals(id)) {
            throw new org.springframework.security.access.AccessDeniedException("Vous n'êtes pas autorisé à modifier les informations de ce compte");
        }
        
        UserResponse updatedUser = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success(updatedUser, "Informations de profil mises à jour avec succès !"));
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
     * Activer un compte utilisateur/manager (Admin seulement).
     * PATCH /api/users/{id}/activate
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activer un compte", description = "Réactiver un compte désactivé (Manager ou Utilisateur)")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(@PathVariable Long id) {
        UserResponse user = userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.success(user, "Compte activé avec succès !"));
    }

    /**
     * Désactiver un compte utilisateur/manager (Admin seulement).
     * PATCH /api/users/{id}/deactivate
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver un compte", description = "Désactiver un compte (Manager ou Utilisateur). Empêche la connexion immédiate.")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(@PathVariable Long id) {
        UserResponse user = userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success(user, "Compte désactivé avec succès !"));
    }

    /**
     * Supprimer définitivement un utilisateur (Admin seulement).
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprimer un compte", description = "Suppression définitive d'un compte")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Compte supprimé avec succès"));
    }
}
