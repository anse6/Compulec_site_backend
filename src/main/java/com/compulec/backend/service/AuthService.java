package com.compulec.backend.service;

import com.compulec.backend.dto.request.LoginRequest;
import com.compulec.backend.dto.request.RegisterRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service de gestion de l'authentification des utilisateurs.
 * Gère l'inscription, la connexion et le renouvellement des tokens.
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

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    /**
     * Inscrit un nouvel utilisateur en base de données.
     *
     * @param request les données d'inscription
     * @return la réponse d'authentification avec les tokens JWT
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Vérifier si l'email existe déjà
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Un compte avec cet email existe déjà : " + request.getEmail());
        }

        // Créer l'utilisateur
        User user = User.builder()
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .telephone(request.getTelephone())
                .role(Role.USER)
                .actif(true)
                .build();

        user = userRepository.save(user);
        log.info("Nouvel utilisateur créé : {}", user.getEmail());

        // Générer les tokens JWT
        String accessToken = jwtService.generateToken(user);
        String refreshToken = saveRefreshToken(user);

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    /**
     * Connecte un utilisateur existant.
     *
     * @param request les identifiants de connexion
     * @return la réponse d'authentification avec les tokens JWT
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Authentifier l'utilisateur (lève BadCredentialsException si invalide)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "email", request.getEmail()));

        // Révoquer les anciens tokens
        refreshTokenRepository.revokeAllUserTokens(user);

        // Générer nouveaux tokens
        String accessToken = jwtService.generateToken(user);
        String refreshToken = saveRefreshToken(user);

        log.info("Connexion réussie pour : {}", user.getEmail());
        return buildAuthResponse(accessToken, refreshToken, user);
    }

    /**
     * Renouvelle l'access token à partir d'un refresh token valide.
     *
     * @param refreshTokenValue le refresh token
     * @return la réponse d'authentification avec le nouvel access token
     */
    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token invalide"));

        if (!refreshToken.isValid()) {
            throw new IllegalArgumentException("Refresh token expiré ou révoqué");
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtService.generateToken(user);

        log.info("Token renouvelé pour : {}", user.getEmail());
        return buildAuthResponse(newAccessToken, refreshTokenValue, user);
    }

    /**
     * Déconnecte l'utilisateur en révoquant tous ses tokens.
     *
     * @param userEmail l'email de l'utilisateur
     */
    @Transactional
    public void logout(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "email", userEmail));
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Déconnexion de : {}", userEmail);
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
                .user(mapToUserResponse(user))
                .build();
    }

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
