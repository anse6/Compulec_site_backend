package com.compulec.backend.service;

import com.compulec.backend.dto.request.CreateManagerRequest;
import com.compulec.backend.dto.response.UserResponse;
import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.Sexe;
import com.compulec.backend.entity.User;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.repository.RefreshTokenRepository;
import com.compulec.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Service de gestion des utilisateurs et des managers par l'Administrateur.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    /**
     * Permet à un Administrateur de créer un compte Manager et lui envoyer un e-mail.
     */
    @Transactional
    public UserResponse createManager(CreateManagerRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Le mot de passe et sa confirmation ne correspondent pas");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Un compte avec cet email existe déjà : " + request.getEmail());
        }

        User manager = User.builder()
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .email(request.getEmail())
                .phone(request.getPhone())
                .dateNaissance(request.getDateNaissance())
                .fonction(request.getFonction())
                .sexe(request.getSexe())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.MANAGER)
                .actif(true)
                .build();

        manager = userRepository.save(manager);
        log.info("Manager créé par l'Admin avec l'email : {}", manager.getEmail());

        // Envoi de l'e-mail de bienvenue au Manager
        try {
            emailService.sendWelcomeEmail(manager.getEmail(), manager.getNom(), manager.getPrenom(), request.getPassword(), "Manager");
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'e-mail de bienvenue au manager {} : {}", manager.getEmail(), e.getMessage());
        }

        return AuthService.mapToUserResponse(manager);
    }

    /**
     * Modifie le rôle d'un utilisateur (réservé aux admins).
     */
    @Transactional
    public UserResponse updateUserRole(Long id, Role newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));

        user.setRole(newRole);
        user = userRepository.save(user);
        log.info("Rôle mis à jour pour l'utilisateur {} : {}", user.getEmail(), newRole);
        return AuthService.mapToUserResponse(user);
    }

    /**
     * Récupère la liste paginée de tous les managers.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getManagers(Pageable pageable) {
        return userRepository.findByRole(Role.MANAGER, pageable)
                .map(AuthService::mapToUserResponse);
    }

    /**
     * Récupère tous les utilisateurs avec pagination.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(AuthService::mapToUserResponse);
    }

    /**
     * Récupère un utilisateur par son ID.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));
        return AuthService.mapToUserResponse(user);
    }

    /**
     * Récupère un utilisateur par son email.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "email", email));
        return AuthService.mapToUserResponse(user);
    }

    /**
     * Met à jour les informations d'un utilisateur.
     */
    @Transactional
    public UserResponse updateUser(Long id, com.compulec.backend.dto.request.UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));

        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        
        if (!request.getPhone().matches("^[0-9]{9}$")) {
            throw new IllegalArgumentException("Le numéro de téléphone doit contenir exactement 9 chiffres");
        }
        user.setPhone(request.getPhone());
        user.setDateNaissance(request.getDateNaissance());
        user.setFonction(request.getFonction());
        user.setSexe(request.getSexe());

        user = userRepository.save(user);
        log.info("Utilisateur mis à jour : {}", user.getEmail());
        return AuthService.mapToUserResponse(user);
    }

    /**
     * Active un utilisateur/manager.
     */
    @Transactional
    public UserResponse activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));

        user.setActif(true);
        user = userRepository.save(user);
        log.info("Compte utilisateur réactivé : {}", user.getEmail());
        return AuthService.mapToUserResponse(user);
    }

    /**
     * Désactive un utilisateur/manager.
     * Révoque immédiatement tous les tokens du compte désactivé.
     */
    @Transactional
    public UserResponse deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));

        user.setActif(false);
        refreshTokenRepository.revokeAllUserTokens(user);
        user = userRepository.save(user);
        log.info("Compte utilisateur désactivé : {}", user.getEmail());
        return AuthService.mapToUserResponse(user);
    }

    /**
     * Supprime définitivement un utilisateur.
     */
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("Utilisateur", "id", id);
        }
        userRepository.deleteById(id);
        log.info("Utilisateur supprimé avec l'ID : {}", id);
    }

    /**
     * Change le mot de passe d'un utilisateur.
     */
    @Transactional
    public void changePassword(Long id, String ancienMotDePasse, String nouveauMotDePasse, String confirmNouveauMotDePasse) {
        if (!nouveauMotDePasse.equals(confirmNouveauMotDePasse)) {
            throw new IllegalArgumentException("Le nouveau mot de passe et sa confirmation ne correspondent pas");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));

        if (!passwordEncoder.matches(ancienMotDePasse, user.getPassword())) {
            throw new IllegalArgumentException("L'ancien mot de passe est incorrect");
        }

        user.setPassword(passwordEncoder.encode(nouveauMotDePasse));
        userRepository.save(user);
        log.info("Mot de passe changé avec succès pour : {}", user.getEmail());
    }
}
