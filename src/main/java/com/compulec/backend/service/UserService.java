package com.compulec.backend.service;

import com.compulec.backend.dto.request.CreateManagerRequest;
import com.compulec.backend.dto.response.ManagerCreatedResponse;
import com.compulec.backend.dto.response.UserResponse;
import com.compulec.backend.entity.AccountTokenType;
import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.Sexe;
import com.compulec.backend.entity.User;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.repository.AccountTokenRepository;
import com.compulec.backend.repository.ChatMessageRepository;
import com.compulec.backend.repository.ChatSessionRepository;
import com.compulec.backend.repository.MfaBackupCodeRepository;
import com.compulec.backend.repository.RefreshTokenRepository;
import com.compulec.backend.security.PasswordPolicy;
import com.compulec.backend.security.SecureTokenGenerator;
import com.compulec.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
    private final InvitationService invitationService;
    private final AuthService authService;
    private final AccountTokenRepository accountTokenRepository;
    private final SecureTokenGenerator tokenGenerator;
    private final MfaService mfaService;
    private final MfaBackupCodeRepository mfaBackupCodeRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * Permet à un Administrateur de créer un compte Manager.
     *
     * Le compte est créé INACTIF, avec un mot de passe aléatoire que personne ne connaît.
     * Le manager reçoit un lien d'invitation par e-mail et doit saisir le code
     * d'activation que l'admin lui transmet par un autre canal (voir InvitationService).
     */
    @Transactional
    public ManagerCreatedResponse createManager(CreateManagerRequest request) {
        String email = request.getEmail().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte avec cet email existe déjà : " + email);
        }

        User manager = User.builder()
                .nom(request.getNom().trim())
                .prenom(request.getPrenom().trim())
                .email(email)
                .phone(request.getPhone())
                .dateNaissance(request.getDateNaissance())
                .fonction(request.getFonction().trim())
                .sexe(request.getSexe())
                .password(passwordEncoder.encode(tokenGenerator.newToken()))
                .role(Role.MANAGER)
                .actif(false)
                .build();

        manager = userRepository.save(manager);
        log.info("Manager {} créé par un administrateur (invitation en attente)", manager.getId());

        return invitationService.issueInvitation(manager);
    }

    /**
     * Renvoie une invitation (nouveau lien + nouveau code) à un compte non encore activé.
     */
    @Transactional
    public ManagerCreatedResponse resendInvitation(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));
        return invitationService.issueInvitation(user);
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

        if (user.getActivatedAt() == null) {
            throw new IllegalArgumentException(
                    "Ce compte n'a pas encore accepté son invitation : renvoyez-lui une invitation au lieu de l'activer.");
        }
        user.setActif(true);
        user = userRepository.save(user);
        log.info("Compte utilisateur réactivé : {}", user.getId());
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
        user.incrementTokenVersion();          // coupe immédiatement tous les access tokens
        refreshTokenRepository.revokeAllUserTokens(user);
        accountTokenRepository.revokeAll(user, AccountTokenType.INVITATION);
        accountTokenRepository.revokeAll(user, AccountTokenType.PASSWORD_RESET);
        user = userRepository.save(user);
        log.info("Compte utilisateur désactivé : {}", user.getEmail());
        return AuthService.mapToUserResponse(user);
    }

    /**
     * Supprime définitivement un utilisateur.
     * Les tokens de rafraîchissement liés sont d'abord supprimés pour éviter
     * la violation de contrainte de clé étrangère (FK refresh_tokens → users).
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));

        // 1. Supprimer physiquement tous les jetons de cet utilisateur
        refreshTokenRepository.deleteAllByUser(user);
        accountTokenRepository.deleteAllByUser(user);
        mfaBackupCodeRepository.deleteAllByUser(user);
        // Conversations et réponses de chat : on les garde, sans lien vers le compte supprimé
        chatSessionRepository.clearAssignedAdmin(user);
        chatMessageRepository.clearAuthor(user);

        // 2. Supprimer l'utilisateur
        userRepository.delete(user);
        log.info("Utilisateur supprimé avec l'ID : {}", id);
    }

    /**
     * Réinitialise la double authentification d'un utilisateur (téléphone perdu).
     */
    @Transactional
    public UserResponse resetMfa(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));
        mfaService.reset(user);
        return AuthService.mapToUserResponse(user);
    }

    /**
     * Change le mot de passe de l'utilisateur connecté.
     * Toutes les autres sessions sont fermées ; une nouvelle session est renvoyée
     * pour que l'utilisateur reste connecté sur l'appareil courant.
     */
    @Transactional
    public AuthService.Session changePassword(Long id, String ancienMotDePasse, String nouveauMotDePasse, String confirmNouveauMotDePasse) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));

        if (!passwordEncoder.matches(ancienMotDePasse, user.getPassword())) {
            throw new IllegalArgumentException("L'ancien mot de passe est incorrect");
        }
        if (passwordEncoder.matches(nouveauMotDePasse, user.getPassword())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'ancien");
        }
        PasswordPolicy.validate(nouveauMotDePasse, confirmNouveauMotDePasse, user);

        user.setPassword(passwordEncoder.encode(nouveauMotDePasse));
        user.incrementTokenVersion();
        user = userRepository.save(user);
        log.info("Mot de passe changé pour l'utilisateur {}", user.getId());

        try {
            emailService.sendPasswordChangedEmail(user);
        } catch (Exception e) {
            log.error("Échec de l'envoi de l'alerte de changement de mot de passe à {} : {}", user.getId(), e.getMessage());
        }
        return authService.startSession(user);
    }
}
