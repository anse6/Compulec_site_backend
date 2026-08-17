package com.compulec.backend.service;

import com.compulec.backend.dto.request.ForgotPasswordRequest;
import com.compulec.backend.dto.request.LoginRequest;
import com.compulec.backend.dto.request.RegisterRequest;
import com.compulec.backend.dto.request.ResetPasswordRequest;
import com.compulec.backend.dto.request.VerifyCodeRequest;
import com.compulec.backend.dto.response.AuthResponse;
import com.compulec.backend.dto.response.UserResponse;
import com.compulec.backend.entity.RefreshToken;
import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.User;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.repository.RefreshTokenRepository;
import com.compulec.backend.repository.UserRepository;
import com.compulec.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * Service de gestion de l'authentification des utilisateurs.
 * Gère l'inscription, la connexion, les jetons JWT, et le mot de passe oublié.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    /**
     * Inscrit un nouveau compte Administrateur en base de données et envoie un mail de bienvenue.
     *
     * @param request les données d'inscription
     * @return la réponse d'authentification avec les tokens JWT
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Le mot de passe et sa confirmation ne correspondent pas");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Un compte avec cet email existe déjà : " + request.getEmail());
        }

        User user = User.builder()
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .email(request.getEmail())
                .phone(request.getPhone())
                .dateNaissance(request.getDateNaissance())
                .fonction(request.getFonction())
                .sexe(request.getSexe())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ADMIN)
                .actif(true)
                .build();

        user = userRepository.save(user);
        log.info("Nouvel Administrateur créé avec succès : {}", user.getEmail());

        // Envoi de l'e-mail de bienvenue
        try {
            emailService.sendWelcomeEmail(user.getEmail(), user.getNom(), user.getPrenom(), request.getPassword(), "Administrateur");
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'e-mail de bienvenue à {} : {}", user.getEmail(), e.getMessage());
        }

        String accessToken = jwtService.generateToken(user);
        String refreshToken = saveRefreshToken(user);

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    /**
     * Connecte un utilisateur (Admin ou Manager).
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "email", request.getEmail()));

        if (!user.isActif()) {
            throw new DisabledException("Votre compte a été désactivé. Veuillez contacter un administrateur.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        refreshTokenRepository.revokeAllUserTokens(user);

        String accessToken = jwtService.generateToken(user);
        String refreshToken = saveRefreshToken(user);

        log.info("Connexion réussie pour : {}", user.getEmail());
        return buildAuthResponse(accessToken, refreshToken, user);
    }

    /**
     * Étape 1 : Demander un code de réinitialisation de mot de passe.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "email", request.getEmail()));

        // Générer un code à 6 chiffres
        String code = String.format("%06d", new Random().nextInt(999999));
        user.setResetPasswordCode(code);
        user.setResetPasswordCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        // Envoyer l'e-mail de réinitialisation
        emailService.sendResetPasswordCodeEmail(user.getEmail(), user.getNom(), user.getPrenom(), code);
        log.info("Code de réinitialisation envoyé à l'adresse : {}", user.getEmail());
    }

    /**
     * Étape 2 : Vérifier le code de réinitialisation (recherche par code uniquement).
     */
    @Transactional
    public AuthResponse verifyResetCode(VerifyCodeRequest request) {
        User user = userRepository.findByResetPasswordCode(request.getCode())
                .orElseThrow(() -> new IllegalArgumentException("Code de vérification incorrect ou inexistant"));

        if (user.getResetPasswordCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Le code de vérification a expiré");
        }

        // Générer un token temporaire pour que l'utilisateur puisse réinitialiser son mot de passe
        String accessToken = jwtService.generateToken(user);
        String refreshToken = saveRefreshToken(user);

        log.info("Code de réinitialisation validé pour l'utilisateur : {}", user.getEmail());
        return buildAuthResponse(accessToken, refreshToken, user);
    }

    /**
     * Étape 3 : Réinitialiser le mot de passe après confirmation du code (via authentification).
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request, UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalArgumentException("Jeton de réinitialisation invalide ou expiré");
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("Le nouveau mot de passe et sa confirmation ne correspondent pas");
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "email", userDetails.getUsername()));

        // Mettre à jour le mot de passe
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        
        // Vider les champs du code de réinitialisation
        user.setResetPasswordCode(null);
        user.setResetPasswordCodeExpiresAt(null);
        
        userRepository.save(user);

        // Révoquer les tokens existants pour forcer la déconnexion
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Mot de passe réinitialisé pour l'utilisateur : {}", user.getEmail());
    }

    /**
     * Renouvelle l'access token à partir d'un refresh token valide.
     */
    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token invalide"));

        if (!refreshToken.isValid()) {
            throw new IllegalArgumentException("Refresh token expiré ou révoqué");
        }

        User user = refreshToken.getUser();

        if (!user.isActif()) {
            throw new DisabledException("Votre compte a été désactivé. Veuillez contacter un administrateur.");
        }

        String newAccessToken = jwtService.generateToken(user);
        log.info("Token renouvelé pour : {}", user.getEmail());

        return buildAuthResponse(newAccessToken, refreshTokenValue, user);
    }

    /**
     * Déconnecte l'utilisateur en révoquant tous ses tokens.
     */
    @Transactional
    public void logout(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "email", userEmail));
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Déconnexion réussie pour : {}", userEmail);
    }

    // ===== Méthodes privées =====

    private String saveRefreshToken(User user) {
        String token = jwtService.generateRefreshToken(user);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
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
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
