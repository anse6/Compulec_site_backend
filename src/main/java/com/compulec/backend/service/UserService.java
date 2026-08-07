package com.compulec.backend.service;

import com.compulec.backend.dto.response.UserResponse;
import com.compulec.backend.entity.User;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des utilisateurs.
 * Fournit les opérations CRUD sur les utilisateurs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Récupère tous les utilisateurs avec pagination.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::mapToUserResponse);
    }

    /**
     * Récupère un utilisateur par son ID.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));
        return mapToUserResponse(user);
    }

    /**
     * Récupère un utilisateur par son email.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "email", email));
        return mapToUserResponse(user);
    }

    /**
     * Met à jour les informations d'un utilisateur.
     */
    @Transactional
    public UserResponse updateUser(Long id, String nom, String prenom, String telephone) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));

        if (nom != null && !nom.isBlank()) user.setNom(nom);
        if (prenom != null && !prenom.isBlank()) user.setPrenom(prenom);
        if (telephone != null) user.setTelephone(telephone);

        user = userRepository.save(user);
        log.info("Utilisateur mis à jour : {}", user.getEmail());
        return mapToUserResponse(user);
    }

    /**
     * Désactive un utilisateur (soft delete).
     */
    @Transactional
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));
        user.setActif(false);
        userRepository.save(user);
        log.info("Utilisateur désactivé : {}", user.getEmail());
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
    public void changePassword(Long id, String ancienMotDePasse, String nouveauMotDePasse) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));

        if (!passwordEncoder.matches(ancienMotDePasse, user.getPassword())) {
            throw new IllegalArgumentException("L'ancien mot de passe est incorrect");
        }

        user.setPassword(passwordEncoder.encode(nouveauMotDePasse));
        userRepository.save(user);
        log.info("Mot de passe changé pour : {}", user.getEmail());
    }

    // ===== Méthodes privées =====

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .email(user.getEmail())
                .telephone(user.getTelephone())
                .role(user.getRole())
                .actif(user.isActif())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
