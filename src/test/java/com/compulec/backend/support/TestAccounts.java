package com.compulec.backend.support;

import com.compulec.backend.dto.request.LoginRequest;
import com.compulec.backend.dto.request.MfaCodeRequest;
import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.Sexe;
import com.compulec.backend.entity.User;
import com.compulec.backend.repository.AccountTokenRepository;
import com.compulec.backend.repository.ChatMessageRepository;
import com.compulec.backend.repository.ChatSessionRepository;
import com.compulec.backend.repository.MfaBackupCodeRepository;
import com.compulec.backend.repository.RefreshTokenRepository;
import com.compulec.backend.repository.UserRepository;
import com.compulec.backend.security.RefreshTokenCookie;
import com.compulec.backend.security.SecretCipher;
import com.compulec.backend.security.TotpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Outils de test : création de comptes (l'inscription publique n'existe plus)
 * et connexion complète en deux étapes (mot de passe + code 2FA).
 */
@Component
@RequiredArgsConstructor
public class TestAccounts {

    public static final String PASSWORD = "Securite-2026-Ok";
    /** Secret TOTP connu des tests (Base32). */
    public static final String TOTP_SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    /** Session obtenue après une connexion complète. */
    public record LoginResult(String accessToken, Cookie refreshCookie, JsonNode body) {
    }

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final MfaBackupCodeRepository mfaBackupCodeRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecretCipher secretCipher;
    private final TotpService totpService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Supprime tous les comptes, leurs jetons et les conversations de chat (à appeler après avoir vidé les autres tables). */
    public void deleteAllAccounts() {
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountTokenRepository.deleteAll();
        mfaBackupCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** Compte actif avec 2FA déjà configurée (secret {@link #TOTP_SECRET}). */
    public User createActiveUser(String email, Role role) {
        User user = baseUser(email, role);
        user.setMfaEnabled(true);
        user.setMfaSecret(secretCipher.encrypt(TOTP_SECRET));
        return userRepository.save(user);
    }

    /** Compte actif dont la 2FA n'est pas encore configurée. */
    public User createActiveUserWithoutMfa(String email, Role role) {
        return userRepository.save(baseUser(email, role));
    }

    /** Code TOTP actuel pour un secret ; l'anti-rejeu de l'utilisateur est remis à zéro. */
    public String currentCode(String email, String secret) {
        userRepository.findByEmail(email).ifPresent(u -> {
            u.setMfaLastUsedStep(null);
            userRepository.save(u);
        });
        return totpService.codeAt(secret, totpService.currentStep());
    }

    /** Étape 1 de la connexion : renvoie le corps "data" (mfa_token…). */
    public JsonNode loginStep1(MockMvc mockMvc, String email, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().email(email).password(password).build())))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    /** Connexion complète (mot de passe + 2FA) d'un compte créé par {@link #createActiveUser}. */
    public LoginResult login(MockMvc mockMvc, String email, String password) throws Exception {
        String mfaToken = loginStep1(mockMvc, email, password).path("mfa_token").asText();
        MockHttpServletResponse response = mockMvc.perform(post("/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MfaCodeRequest.builder()
                                .mfaToken(mfaToken).code(currentCode(email, TOTP_SECRET)).build())))
                .andReturn().getResponse();
        JsonNode data = objectMapper.readTree(response.getContentAsString()).path("data");
        return new LoginResult(data.path("access_token").asText(), response.getCookie(RefreshTokenCookie.NAME), data);
    }

    private User baseUser(String email, Role role) {
        return User.builder()
                .nom("Diallo")
                .prenom("Mamadou")
                .email(email)
                .phone("699001122")
                .dateNaissance(LocalDate.of(1990, 1, 15))
                .fonction("Administrateur")
                .sexe(Sexe.HOMME)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .actif(true)
                .activatedAt(LocalDateTime.now())
                .build();
    }
}
