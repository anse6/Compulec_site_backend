package com.compulec.backend.controller;

import com.compulec.backend.dto.request.AcceptInvitationRequest;
import com.compulec.backend.dto.request.ForgotPasswordRequest;
import com.compulec.backend.dto.request.LoginRequest;
import com.compulec.backend.dto.request.ResetPasswordRequest;
import com.compulec.backend.dto.request.TokenRequest;
import com.compulec.backend.entity.AccountToken;
import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.User;
import com.compulec.backend.repository.AccountTokenRepository;
import com.compulec.backend.repository.RefreshTokenRepository;
import com.compulec.backend.repository.UserRepository;
import com.compulec.backend.service.EmailService;
import com.compulec.backend.dto.request.MfaCodeRequest;
import com.compulec.backend.entity.RefreshToken;
import com.compulec.backend.security.RefreshTokenCookie;
import com.compulec.backend.security.TotpService;
import com.compulec.backend.support.TestAccounts;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de l'authentification et du flux d'invitation sécurisé.
 * Structure JSON des réponses :
 *   { "success": true, "data": { "access_token": "...", "user": { "email": "...", "role": "..." } } }
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@compulec.com";
    private static final String MANAGER_EMAIL = "nouveau.manager@compulec.com";
    private static final String MANAGER_PASSWORD = "Bureau-Douala-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AccountTokenRepository accountTokenRepository;

    @Autowired
    private TestAccounts testAccounts;

    @Autowired
    private TotpService totpService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Mock EmailService : aucune connexion SMTP, et permet de capturer les liens envoyés
    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        testAccounts.deleteAllAccounts();
        testAccounts.createActiveUser(ADMIN_EMAIL, Role.ADMIN);
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private ResultActions postJson(String url, Object body, String bearer) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(request);
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/auth/login", LoginRequest.builder().email(email).password(password).build(), null);
    }

    private String adminToken() throws Exception {
        return testAccounts.login(mockMvc, ADMIN_EMAIL, TestAccounts.PASSWORD).accessToken();
    }

    private ResultActions verifyMfa(String mfaToken, String code) throws Exception {
        return postJson("/auth/mfa/verify", MfaCodeRequest.builder().mfaToken(mfaToken).code(code).build(), null);
    }

    private String mfaToken(String email, String password) throws Exception {
        return testAccounts.loginStep1(mockMvc, email, password).path("mfa_token").asText();
    }

    private MockHttpServletResponse refresh(Cookie cookie) throws Exception {
        var request = post("/auth/refresh-token");
        if (cookie != null) {
            request.cookie(cookie);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private Map<String, Object> managerPayload() {
        return Map.of(
                "nom", "Ngono",
                "prenom", "Claire",
                "email", MANAGER_EMAIL,
                "phone", "677889900",
                "dateNaissance", "1992-04-12",
                "fonction", "Responsable Commercial",
                "sexe", "FEMME");
    }

    /** Invite un manager et renvoie { jeton du lien, code d'activation }. */
    private String[] inviteManager() throws Exception {
        String body = postJson("/users/managers", managerPayload(), adminToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.pendingInvitation").value(true))
                .andExpect(jsonPath("$.data.user.actif").value(false))
                .andExpect(jsonPath("$.data.activationCode").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(body).path("data").path("activationCode").asText();
        return new String[]{lastInvitationToken(), code};
    }

    private String lastInvitationToken() {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce()).sendInvitationEmail(any(User.class), link.capture(), any());
        String url = link.getValue();
        assertTrue(url.startsWith("http://localhost:5173/activate-account#"), "Lien inattendu : " + url);
        return url.substring(url.indexOf('#') + 1);
    }

    private AcceptInvitationRequest accept(String token, String code, String password) {
        return AcceptInvitationRequest.builder()
                .token(token).code(code).password(password).confirmPassword(password).build();
    }

    // ===================================================================
    // Connexion
    // ===================================================================

    @Test
    void publicRegistration_NoLongerExists() throws Exception {
        postJson("/auth/register", managerPayload(), null)
                .andExpect(status().isUnauthorized());
        assertEquals(1, userRepository.count());
    }

    @Test
    void login_PasswordAloneGivesNoSession_OnlyMfaChallenge() throws Exception {
        MockHttpServletResponse response = login(ADMIN_EMAIL, TestAccounts.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfa_required").value(true))
                .andExpect(jsonPath("$.data.mfa_token").isNotEmpty())
                .andExpect(jsonPath("$.data.access_token").doesNotExist())
                .andReturn().getResponse();
        assertNull(response.getCookie(RefreshTokenCookie.NAME));
    }

    @Test
    void login_FullFlow_RefreshTokenOnlyInHttpOnlyCookie() throws Exception {
        TestAccounts.LoginResult result = testAccounts.login(mockMvc, ADMIN_EMAIL, TestAccounts.PASSWORD);
        assertFalse(result.accessToken().isBlank());
        assertTrue(result.body().path("refresh_token").isMissingNode(), "Le refresh token ne doit pas être dans le JSON");
        assertNotNull(result.refreshCookie());
        assertTrue(result.refreshCookie().isHttpOnly());
        assertEquals("/api/auth", result.refreshCookie().getPath());
    }

    @Test
    void login_WrongPasswordAndUnknownEmail_SameGenericAnswer() throws Exception {
        login(ADMIN_EMAIL, "Mauvais-mot-2026")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
        login("inconnu@compulec.com", "Mauvais-mot-2026")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
    }

    @Test
    void login_LockedAfterFiveFailures_EvenWithCorrectPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(ADMIN_EMAIL, "Mauvais-mot-2026").andExpect(status().isUnauthorized());
        }
        login(ADMIN_EMAIL, TestAccounts.PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message", containsString("Réessayez")));
        verify(emailService).sendAccountLockedEmail(any(User.class), any());
    }

    @Test
    void refreshAndMfaTokens_CannotBeUsedAsAccessToken() throws Exception {
        String refreshToken = testAccounts.login(mockMvc, ADMIN_EMAIL, TestAccounts.PASSWORD).refreshCookie().getValue();
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + mfaToken(ADMIN_EMAIL, TestAccounts.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // ===================================================================
    // Double authentification
    // ===================================================================

    @Test
    void mfa_WrongCodesCountTowardsLockout() throws Exception {
        String token = mfaToken(ADMIN_EMAIL, TestAccounts.PASSWORD);
        String wrong = testAccounts.currentCode(ADMIN_EMAIL, TestAccounts.TOTP_SECRET).equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            verifyMfa(token, wrong)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Code de double authentification incorrect"));
        }
        // Même le bon code est refusé : le compte est verrouillé
        verifyMfa(token, testAccounts.currentCode(ADMIN_EMAIL, TestAccounts.TOTP_SECRET)).andExpect(status().isLocked());
        login(ADMIN_EMAIL, TestAccounts.PASSWORD).andExpect(status().isLocked());
    }

    @Test
    void mfa_SameCodeCannotBeReplayed() throws Exception {
        String code = testAccounts.currentCode(ADMIN_EMAIL, TestAccounts.TOTP_SECRET);
        verifyMfa(mfaToken(ADMIN_EMAIL, TestAccounts.PASSWORD), code).andExpect(status().isOk());
        verifyMfa(mfaToken(ADMIN_EMAIL, TestAccounts.PASSWORD), code).andExpect(status().isUnauthorized());
    }

    @Test
    void mfa_IsOptional_WithoutMfaPasswordGivesSessionDirectly() throws Exception {
        testAccounts.createActiveUserWithoutMfa(MANAGER_EMAIL, Role.MANAGER);

        MockHttpServletResponse response = login(MANAGER_EMAIL, TestAccounts.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.mfa_required").doesNotExist())
                .andExpect(jsonPath("$.data.mfa_setup_available").value(true))
                .andReturn().getResponse();
        assertNotNull(response.getCookie(RefreshTokenCookie.NAME), "La session doit être ouverte (cookie)");

        String accessToken = objectMapper.readTree(response.getContentAsString()).path("data").path("access_token").asText();
        // « Pas maintenant » : l'utilisateur accède directement au tableau de bord
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaEnabled").value(false));
    }

    @Test
    void mfa_OptionalSetup_ThenRequiredAtEachLogin_BackupCodesSingleUse() throws Exception {
        testAccounts.createActiveUserWithoutMfa(MANAGER_EMAIL, Role.MANAGER);

        String body = login(MANAGER_EMAIL, TestAccounts.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfa_setup_available").value(true))
                .andReturn().getResponse().getContentAsString();
        String setupToken = objectMapper.readTree(body).path("data").path("mfa_token").asText();

        // Le jeton d'enrôlement ne permet pas de passer l'étape 2FA classique
        verifyMfa(setupToken, "123456").andExpect(status().isUnauthorized());

        String setup = postJson("/auth/mfa/setup", new TokenRequest(setupToken), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpauthUri", containsString("otpauth://totp/COMPULEC")))
                .andReturn().getResponse().getContentAsString();
        String secret = objectMapper.readTree(setup).path("data").path("secret").asText();

        postJson("/auth/mfa/setup/confirm", MfaCodeRequest.builder().mfaToken(setupToken).code("000000").build(), null)
                .andExpect(status().isBadRequest());

        String confirmed = postJson("/auth/mfa/setup/confirm", MfaCodeRequest.builder()
                        .mfaToken(setupToken).code(totpService.codeAt(secret, totpService.currentStep())).build(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.backup_codes.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        String backupCode = objectMapper.readTree(confirmed).path("data").path("backup_codes").get(0).asText();
        verify(emailService).sendMfaEnabledEmail(any(User.class));

        // Une fois activée, la 2FA est exigée : le mot de passe seul ne suffit plus
        login(MANAGER_EMAIL, TestAccounts.PASSWORD)
                .andExpect(jsonPath("$.data.mfa_required").value(true))
                .andExpect(jsonPath("$.data.access_token").doesNotExist());

        // Un code de secours fonctionne une seule fois
        verifyMfa(mfaToken(MANAGER_EMAIL, TestAccounts.PASSWORD), backupCode.toLowerCase()).andExpect(status().isOk());
        verifyMfa(mfaToken(MANAGER_EMAIL, TestAccounts.PASSWORD), backupCode).andExpect(status().isUnauthorized());
        verify(emailService).sendBackupCodeUsedEmail(any(User.class), eq(9L));
    }

    @Test
    void mfa_AdminResetClosesSessionsAndRemovesMfa() throws Exception {
        testAccounts.createActiveUser(MANAGER_EMAIL, Role.MANAGER);
        String managerToken = testAccounts.login(mockMvc, MANAGER_EMAIL, TestAccounts.PASSWORD).accessToken();
        Long managerId = userRepository.findByEmail(MANAGER_EMAIL).orElseThrow().getId();

        mockMvc.perform(patch("/users/" + managerId + "/mfa/reset").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isUnauthorized());
        // 2FA supprimée : connexion directe, avec la proposition de la réactiver
        login(MANAGER_EMAIL, TestAccounts.PASSWORD)
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.mfa_setup_available").value(true));
        verify(emailService).sendMfaResetEmail(any(User.class));
    }

    @Test
    void mfa_ManagerCannotResetSomeoneElse() throws Exception {
        testAccounts.createActiveUser(MANAGER_EMAIL, Role.MANAGER);
        String managerToken = testAccounts.login(mockMvc, MANAGER_EMAIL, TestAccounts.PASSWORD).accessToken();
        Long adminId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        mockMvc.perform(patch("/users/" + adminId + "/mfa/reset").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    // ===================================================================
    // Refresh token : cookie HttpOnly + rotation + détection de vol
    // ===================================================================

    @Test
    void refresh_WithoutCookie_Unauthorized() throws Exception {
        assertEquals(401, refresh(null).getStatus());
    }

    @Test
    void refresh_RotatesToken_AndDetectsTheftOfAnOldOne() throws Exception {
        Cookie first = testAccounts.login(mockMvc, ADMIN_EMAIL, TestAccounts.PASSWORD).refreshCookie();

        MockHttpServletResponse r1 = refresh(first);
        assertEquals(200, r1.getStatus());
        Cookie second = r1.getCookie(RefreshTokenCookie.NAME);
        assertNotEquals(first.getValue(), second.getValue());

        // Réutilisation immédiate (requêtes simultanées) : refusée, mais la session continue
        assertEquals(401, refresh(first).getStatus());
        MockHttpServletResponse r2 = refresh(second);
        assertEquals(200, r2.getStatus());
        Cookie third = r2.getCookie(RefreshTokenCookie.NAME);

        // Plus tard, quelqu'un rejoue l'ancien jeton : vol présumé → toutes les sessions sont fermées
        List<RefreshToken> all = refreshTokenRepository.findAll();
        all.forEach(t -> { if (t.getReplacedAt() != null) t.setReplacedAt(LocalDateTime.now().minusMinutes(5)); });
        refreshTokenRepository.saveAll(all);

        assertEquals(401, refresh(first).getStatus());
        assertEquals(401, refresh(third).getStatus());
    }

    @Test
    void logout_ClearsCookieAndRevokesSession() throws Exception {
        Cookie cookie = testAccounts.login(mockMvc, ADMIN_EMAIL, TestAccounts.PASSWORD).refreshCookie();

        MockHttpServletResponse response = mockMvc.perform(post("/auth/logout").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(0, response.getCookie(RefreshTokenCookie.NAME).getMaxAge());
        assertEquals(401, refresh(cookie).getStatus());
    }

    // ===================================================================
    // Invitation des managers
    // ===================================================================

    @Test
    void invitation_FullFlow_ManagerChoosesOwnPassword() throws Exception {
        String[] invitation = inviteManager();
        String token = invitation[0];
        String code = invitation[1];

        // Le compte n'existe pas encore pour la connexion
        login(MANAGER_EMAIL, MANAGER_PASSWORD).andExpect(status().isUnauthorized());

        // L'aperçu ne révèle qu'un e-mail masqué
        postJson("/auth/invitations/preview", new TokenRequest(token), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prenom").value("Claire"))
                .andExpect(jsonPath("$.data.maskedEmail").value("n•••r@compulec.com"));

        // Mot de passe trop faible → refusé, l'invitation reste valable
        postJson("/auth/invitations/accept", accept(token, code, "court1"), null)
                .andExpect(status().isBadRequest());

        // Activation
        postJson("/auth/invitations/accept", accept(token, code, MANAGER_PASSWORD), null)
                .andExpect(status().isOk());

        // Première connexion : accès direct, la 2FA est proposée (facultative)
        login(MANAGER_EMAIL, MANAGER_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.mfa_setup_available").value(true));

        verify(emailService).sendAccountActivatedEmail(any(User.class));
        verify(emailService).sendAccountActivatedAdminNotice(any(User.class), any(User.class));

        // Le lien est à usage unique
        postJson("/auth/invitations/accept", accept(token, code, "Autre-Bureau-2026"), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void invitation_LinkAloneIsUseless_RevokedAfterFiveWrongCodes() throws Exception {
        String[] invitation = inviteManager();
        String wrongCode = invitation[1].equals("000000") ? "111111" : "000000";

        for (int i = 1; i <= 4; i++) {
            postJson("/auth/invitations/accept", accept(invitation[0], wrongCode, MANAGER_PASSWORD), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Tentatives restantes : " + (5 - i))));
        }
        postJson("/auth/invitations/accept", accept(invitation[0], wrongCode, MANAGER_PASSWORD), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("annulée")));

        // Même avec le bon code, l'invitation est désormais morte
        postJson("/auth/invitations/accept", accept(invitation[0], invitation[1], MANAGER_PASSWORD), null)
                .andExpect(status().isBadRequest());
        verify(emailService).sendInvitationRevokedAdminNotice(any(User.class), any(User.class));
    }

    @Test
    void invitation_ResendInvalidatesPreviousLinkAndCode() throws Exception {
        String[] first = inviteManager();
        Long managerId = userRepository.findByEmail(MANAGER_EMAIL).orElseThrow().getId();

        String body = postJson("/users/" + managerId + "/invitation", Map.of(), adminToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondCode = objectMapper.readTree(body).path("data").path("activationCode").asText();
        String secondToken = lastInvitationToken();

        postJson("/auth/invitations/preview", new TokenRequest(first[0]), null)
                .andExpect(status().isBadRequest());
        postJson("/auth/invitations/accept", accept(secondToken, secondCode, MANAGER_PASSWORD), null)
                .andExpect(status().isOk());
    }

    @Test
    void invitation_ExpiredLinkIsRejected() throws Exception {
        String[] invitation = inviteManager();
        AccountToken stored = accountTokenRepository.findAll().get(0);
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        accountTokenRepository.save(stored);

        postJson("/auth/invitations/accept", accept(invitation[0], invitation[1], MANAGER_PASSWORD), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("expiré")));
    }

    @Test
    void invitation_SecretsAreNeverStoredInClear() throws Exception {
        String[] invitation = inviteManager();
        AccountToken stored = accountTokenRepository.findAll().get(0);
        assertNotEquals(invitation[0], stored.getTokenHash());
        assertNotEquals(invitation[1], stored.getCodeHash());
        assertEquals(64, stored.getTokenHash().length());
    }

    @Test
    void invitation_PendingAccountCannotBeActivatedByAdminToggle() throws Exception {
        inviteManager();
        Long managerId = userRepository.findByEmail(MANAGER_EMAIL).orElseThrow().getId();
        mockMvc.perform(patch("/users/" + managerId + "/activate").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    // ===================================================================
    // Révocation immédiate
    // ===================================================================

    @Test
    void deactivation_RevokesAccessTokenImmediately() throws Exception {
        testAccounts.createActiveUser(MANAGER_EMAIL, Role.MANAGER);
        String managerToken = testAccounts.login(mockMvc, MANAGER_EMAIL, TestAccounts.PASSWORD).accessToken();
        Long managerId = userRepository.findByEmail(MANAGER_EMAIL).orElseThrow().getId();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/users/" + managerId + "/deactivate").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_ClosesOtherSessionsAndReturnsNewOne() throws Exception {
        String oldToken = adminToken();
        Map<String, String> change = Map.of(
                "ancienMotDePasse", TestAccounts.PASSWORD,
                "nouveauMotDePasse", "Nouveau-Code-7788",
                "confirmNouveauMotDePasse", "Nouveau-Code-7788");

        String body = mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(change)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newToken = objectMapper.readTree(body).path("data").path("access_token").asText();
        assertFalse(newToken.isBlank());

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    // ===================================================================
    // Mot de passe oublié
    // ===================================================================

    @Test
    void forgotPassword_UnknownEmail_SameAnswerAndNoEmail() throws Exception {
        postJson("/auth/forgot-password", ForgotPasswordRequest.builder().email("inconnu@compulec.com").build(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(emailService, never()).sendPasswordResetLinkEmail(any(), any(), any());
    }

    @Test
    void forgotPassword_FullFlow_LinkIsSingleUseAndClosesSessions() throws Exception {
        String oldToken = adminToken();

        postJson("/auth/forgot-password", ForgotPasswordRequest.builder().email(ADMIN_EMAIL).build(), null)
                .andExpect(status().isOk());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetLinkEmail(any(User.class), link.capture(), any());
        assertTrue(link.getValue().startsWith("http://localhost:5173/reset-password#"));
        String token = link.getValue().substring(link.getValue().indexOf('#') + 1);

        ResetPasswordRequest reset = ResetPasswordRequest.builder()
                .token(token).newPassword("Nouveau-Code-7788").confirmNewPassword("Nouveau-Code-7788").build();
        postJson("/auth/reset-password", reset, null).andExpect(status().isOk());

        // Anciennes sessions fermées, ancien mot de passe refusé, nouveau accepté
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        login(ADMIN_EMAIL, TestAccounts.PASSWORD).andExpect(status().isUnauthorized());
        login(ADMIN_EMAIL, "Nouveau-Code-7788").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfa_required").value(true));
        verify(emailService).sendPasswordChangedEmail(any(User.class));

        // Lien à usage unique
        postJson("/auth/reset-password", reset, null).andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword_PendingInvitationAccount_GetsNoResetLink() throws Exception {
        inviteManager();
        postJson("/auth/forgot-password", ForgotPasswordRequest.builder().email(MANAGER_EMAIL).build(), null)
                .andExpect(status().isOk());
        verify(emailService, never()).sendPasswordResetLinkEmail(any(), any(), any());
    }
}
