package com.compulec.backend.controller;

import com.compulec.backend.entity.ChatSession;
import com.compulec.backend.entity.ChatSessionStatus;
import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.User;
import com.compulec.backend.repository.ChatSessionRepository;
import com.compulec.backend.repository.ContactRepository;
import com.compulec.backend.service.ChatService;
import com.compulec.backend.repository.UserRepository;
import com.compulec.backend.service.EmailService;
import com.compulec.backend.support.TestAccounts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complet du chat : visiteur → bot → transfert → prise en charge → réponses signées → clôture.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ChatControllerIntegrationTest {

    private static final String ADMIN = "admin.chat@compulec.com";
    private static final String MANAGER = "manager.chat@compulec.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestAccounts testAccounts;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ChatService chatService;

    @MockitoBean
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String adminToken;
    private String managerToken;

    @BeforeEach
    void setUp() throws Exception {
        testAccounts.deleteAllAccounts();
        testAccounts.createActiveUser(ADMIN, Role.ADMIN);
        User manager = testAccounts.createActiveUser(MANAGER, Role.MANAGER);
        manager.setPrenom("Claire");
        manager.setNom("Ngono");
        userRepository.save(manager);
        // Un manager désactivé ne doit pas être alerté
        User inactive = testAccounts.createActiveUser("inactif@compulec.com", Role.MANAGER);
        inactive.setActif(false);
        userRepository.save(inactive);

        adminToken = testAccounts.login(mockMvc, ADMIN, TestAccounts.PASSWORD).accessToken();
        managerToken = testAccounts.login(mockMvc, MANAGER, TestAccounts.PASSWORD).accessToken();
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private ResultActions postJson(String url, Object body, String bearer) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(request);
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    private String startChat(String name) throws Exception {
        return data(postJson("/chat/start", Map.of("visitorName", name, "visitorEmail", "visiteur@test.com"), null)
                .andExpect(status().isOk())).path("sessionToken").asText();
    }

    private ResultActions visitorSays(String token, String text) throws Exception {
        return postJson("/chat/message", Map.of("sessionToken", token, "message", text), null);
    }

    private ResultActions staffGet(String url, String bearer) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + bearer));
    }

    // ===================================================================
    // Parcours complet
    // ===================================================================

    @Test
    void fullConversation_TransferTakeOverSignedRepliesUnreadAndClose() throws Exception {
        String token = startChat("Paul");

        // 5 questions au bot → proposition de conseiller
        for (int i = 1; i <= 4; i++) {
            visitorSays(token, "Question " + i).andExpect(jsonPath("$.data.showAdminTransfer").value(false));
        }
        visitorSays(token, "Quels sont vos tarifs ?").andExpect(jsonPath("$.data.showAdminTransfer").value(true));

        // Transfert : toute l'équipe active est alertée (admin + manager, pas le manager désactivé)
        postJson("/chat/request-admin/" + token, Map.of(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_ADMIN"))
                .andExpect(jsonPath("$.data.assignedAdminName").doesNotExist());
        // E-mails envoyés en arrière-plan : on attend leur envoi
        verify(emailService, org.mockito.Mockito.timeout(3000).times(2))
                .sendAdminChatNotificationEmail(any(User.class), any(ChatSession.class));
        verify(emailService, never()).sendAdminChatNotificationEmail(
                argThat(u -> u != null && "inactif@compulec.com".equals(u.getEmail())), any(ChatSession.class));

        // Visible dans « En attente » avec un non-lu
        staffGet("/chat/admin/sessions?status=WAITING_ADMIN", adminToken)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].unreadCount").value(1))
                .andExpect(jsonPath("$.data.content[0].lastMessagePreview", containsString("conseiller COMPULEC")));

        // La manager prend en charge : le visiteur voit son prénom, jamais son email
        postJson("/chat/admin/take/" + token, Map.of(), managerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITH_ADMIN"))
                .andExpect(jsonPath("$.data.assignedAdminName").value("Claire Ngono"));
        mockMvc.perform(get("/chat/history/" + token))
                .andExpect(jsonPath("$.data.assignedAdminName").value("Claire"))
                .andExpect(jsonPath("$.data.assignedAdminEmail").doesNotExist())
                .andExpect(jsonPath("$.data.messages[-1].content", containsString("Claire de l'équipe COMPULEC a rejoint")));

        // Elle répond : message signé, non-lus remis à zéro
        postJson("/chat/admin/reply", Map.of("sessionToken", token, "message", "Bonjour Paul, je regarde ça."), managerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[-1].senderType").value("ADMIN"))
                .andExpect(jsonPath("$.data.messages[-1].authorName").value("Claire Ngono"))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
        mockMvc.perform(get("/chat/history/" + token))
                .andExpect(jsonPath("$.data.messages[-1].authorName").value("Claire"));

        // Le visiteur répond : le bot se tait, 2 non-lus, puis lecture
        visitorSays(token, "Merci !").andExpect(jsonPath("$.data.sessionStatus").value("WITH_ADMIN"));
        visitorSays(token, "J'attends votre devis.");
        staffGet("/chat/admin/sessions/counts", adminToken)
                .andExpect(jsonPath("$.data.WITH_ADMIN").value(1))
                .andExpect(jsonPath("$.data.UNREAD").value(2));
        mockMvc.perform(patch("/chat/admin/read/" + token).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        staffGet("/chat/admin/sessions/counts", adminToken).andExpect(jsonPath("$.data.UNREAD").value(0));

        // Clôture : le visiteur est prévenu, ne peut plus écrire (409), mais peut recommencer
        mockMvc.perform(patch("/chat/admin/close/" + token).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/chat/history/" + token))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.messages[-1].content", startsWith("Cette conversation a été clôturée")));
        visitorSays(token, "Encore là ?").andExpect(status().isConflict());
        postJson("/chat/admin/reply", Map.of("sessionToken", token, "message", "?"), adminToken)
                .andExpect(status().isConflict());
        assertNotEquals(token, startChat("Paul"));
    }

    @Test
    void replyWithoutTakeOver_AssignsTheReplier_AndColleagueCanTakeOverLater() throws Exception {
        String token = startChat("Awa");
        postJson("/chat/request-admin/" + token, Map.of(), null).andExpect(status().isOk());

        postJson("/chat/admin/reply", Map.of("sessionToken", token, "message", "Bonjour"), adminToken)
                .andExpect(jsonPath("$.data.assignedAdminEmail").value(ADMIN));

        postJson("/chat/admin/take/" + token, Map.of(), managerToken)
                .andExpect(jsonPath("$.data.assignedAdminEmail").value(MANAGER));
    }

    @Test
    void staffCanJoinAConversationStillWithTheBot() throws Exception {
        String token = startChat("Ibrahim");
        visitorSays(token, "Bonjour");
        staffGet("/chat/admin/sessions?status=ACTIVE", adminToken)
                .andExpect(jsonPath("$.data.content[0].sessionToken").value(token));

        postJson("/chat/admin/take/" + token, Map.of(), adminToken)
                .andExpect(jsonPath("$.data.status").value("WITH_ADMIN"));
        // Désormais le bot ne répond plus
        visitorSays(token, "Vous êtes là ?").andExpect(jsonPath("$.data.botReply", containsString("transmis")));
    }

    // ===================================================================
    // Recherche, pagination, erreurs
    // ===================================================================

    @Test
    void search_FiltersByNameEmailOrToken_AndPaginates() throws Exception {
        for (int i = 0; i < 23; i++) {
            startChat("Visiteur " + i);
        }
        String target = startChat("Mireille Ekambi");

        staffGet("/chat/admin/sessions?status=ACTIVE", adminToken)
                .andExpect(jsonPath("$.data.content.length()").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(24))
                // tri par dernière activité : la plus récente en premier
                .andExpect(jsonPath("$.data.content[0].visitorName").value("Mireille Ekambi"));
        staffGet("/chat/admin/sessions?status=ACTIVE&page=1", adminToken)
                .andExpect(jsonPath("$.data.content.length()").value(4));

        staffGet("/chat/admin/sessions?status=ACTIVE&q=ekamb", adminToken)
                .andExpect(jsonPath("$.data.totalElements").value(1));
        staffGet("/chat/admin/sessions?status=ACTIVE&q=" + target.substring(0, 8).toUpperCase(), adminToken)
                .andExpect(jsonPath("$.data.content[0].sessionToken").value(target));
        staffGet("/chat/admin/sessions?status=INCONNU", adminToken).andExpect(status().isBadRequest());
    }

    @Test
    void unknownSession_GivesClearError_ForTheWidgetToReset() throws Exception {
        mockMvc.perform(get("/chat/history/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Session de chat introuvable"));
    }

    @Test
    void staffEndpoints_RequireStaff() throws Exception {
        mockMvc.perform(get("/chat/admin/sessions?status=ACTIVE")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/chat/admin/take/abc")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAStaffMember_KeepsTheirConversations() throws Exception {
        String token = startChat("Lucie");
        postJson("/chat/admin/reply", Map.of("sessionToken", token, "message", "Bonjour Lucie"), managerToken)
                .andExpect(status().isOk());
        Long managerId = userRepository.findByEmail(MANAGER).orElseThrow().getId();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/users/" + managerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        ChatSession session = chatSessionRepository.findBySessionToken(token).orElseThrow();
        assertEquals(ChatSessionStatus.WITH_ADMIN, session.getStatus());
        mockMvc.perform(get("/chat/history/" + token))
                .andExpect(jsonPath("$.data.messages[?(@.content == 'Bonjour Lucie')]").exists());
    }

    // ===================================================================
    // Temps réel (Server-Sent Events)
    // ===================================================================

    @Test
    void visitorStream_ReceivesEventsInstantly() throws Exception {
        String token = startChat("Nadia");
        MvcResult stream = mockMvc.perform(get("/chat/stream/" + token))
                .andExpect(request().asyncStarted())
                .andReturn();

        visitorSays(token, "Bonjour").andExpect(status().isOk());
        postJson("/chat/admin/reply", Map.of("sessionToken", token, "message", "Bonjour Nadia"), adminToken);

        String events = stream.getResponse().getContentAsString();
        assertTrue(events.contains("event:ready"), events);
        assertTrue(events.contains("event:chat"), events);
        assertTrue(events.contains("\"type\":\"VISITOR_MESSAGE\""), events);
        assertTrue(events.contains("\"status\":\"WITH_ADMIN\""), events);
        // Le flux visiteur ne transporte aucune donnée de l'équipe
        assertFalse(events.contains("sessionToken"), events);
        assertFalse(events.contains(ADMIN), events);
    }

    @Test
    void visitorStream_UnknownSessionRejected() throws Exception {
        mockMvc.perform(get("/chat/stream/inconnue")).andExpect(status().isBadRequest());
    }

    @Test
    void staffStream_RequiresStaff_AndAnnouncesTransfers() throws Exception {
        mockMvc.perform(get("/chat/admin/stream")).andExpect(status().isUnauthorized());

        MvcResult stream = mockMvc.perform(get("/chat/admin/stream").header("Authorization", "Bearer " + managerToken))
                .andExpect(request().asyncStarted())
                .andReturn();

        String token = startChat("Sylvie");
        postJson("/chat/request-admin/" + token, Map.of(), null).andExpect(status().isOk());

        String events = stream.getResponse().getContentAsString();
        assertTrue(events.contains("\"type\":\"TRANSFER_REQUESTED\""), events);
        assertTrue(events.contains("\"visitorName\":\"Sylvie\""), events);
        assertTrue(events.contains(token), events);
    }

    // ===================================================================
    // Maintenance automatique
    // ===================================================================

    private void makeInactive(String token, int minutes) {
        ChatSession session = chatSessionRepository.findBySessionToken(token).orElseThrow();
        session.setLastActivityAt(java.time.LocalDateTime.now().minusMinutes(minutes));
        chatSessionRepository.save(session);
    }

    @Test
    void unansweredTransfer_TeamRemindedOnce_VisitorReassured() throws Exception {
        String token = startChat("Eric");
        postJson("/chat/request-admin/" + token, Map.of(), null);
        makeInactive(token, 20);

        assertEquals(1, chatService.remindUnansweredTransfers(java.time.Duration.ofMinutes(15)));
        verify(emailService, times(2)).sendChatWaitingReminderEmail(any(User.class), any(ChatSession.class), any());
        mockMvc.perform(get("/chat/history/" + token))
                .andExpect(jsonPath("$.data.messages[-1].content", containsString("très sollicités")));

        makeInactive(token, 40);
        assertEquals(0, chatService.remindUnansweredTransfers(java.time.Duration.ofMinutes(15)), "Une seule relance");
    }

    @Test
    void inactiveConversations_AreClosed_UnansweredRequestBecomesAContactMessage() throws Exception {
        String botOnly = startChat("Bot Seul");
        String unanswered = startChat("Sans Réponse");
        postJson("/chat/request-admin/" + unanswered, Map.of(), null);
        String answered = startChat("Avec Conseiller");
        postJson("/chat/admin/reply", Map.of("sessionToken", answered, "message", "Bonjour !"), adminToken);
        String recent = startChat("Récent");
        makeInactive(botOnly, 25 * 60);
        makeInactive(unanswered, 49 * 60);
        makeInactive(answered, 49 * 60);
        long contactsBefore = contactRepository.count();

        assertEquals(3, chatService.closeInactive(java.time.Duration.ofHours(24), java.time.Duration.ofHours(48)));

        for (String token : List.of(botOnly, unanswered, answered)) {
            assertEquals(ChatSessionStatus.CLOSED, chatSessionRepository.findBySessionToken(token).orElseThrow().getStatus());
        }
        assertEquals(ChatSessionStatus.ACTIVE, chatSessionRepository.findBySessionToken(recent).orElseThrow().getStatus());
        // La demande jamais traitée est rangée dans « Messages » ; la conversation avec un conseiller est envoyée au visiteur
        assertEquals(contactsBefore + 1, contactRepository.count());
        verify(emailService, times(1)).sendChatTranscriptEmail(any(ChatSession.class), any());
    }

    @Test
    void purge_DeletesOnlyOldClosedConversations() throws Exception {
        String old = startChat("Ancien");
        String fresh = startChat("Récent");
        for (String token : List.of(old, fresh)) {
            mockMvc.perform(patch("/chat/admin/close/" + token).header("Authorization", "Bearer " + adminToken));
        }
        makeInactive(old, 400 * 24 * 60);

        assertEquals(1, chatService.purgeClosedOlderThan(java.time.Duration.ofDays(365)));
        assertTrue(chatSessionRepository.findBySessionToken(old).isEmpty());
        assertTrue(chatSessionRepository.findBySessionToken(fresh).isPresent());
    }

    // ===================================================================
    // Clics multiples sur « Oui, contacter un humain »
    // ===================================================================

    @Test
    void transferRequestedFiveTimesAtOnce_OnlyOneConfirmationAndOneAlertPerMember() throws Exception {
        String token = startChat("Clic Multiple");
        // Envoi des e-mails lent (comme un vrai serveur SMTP) : laisse le temps aux clics de se chevaucher
        org.mockito.Mockito.doAnswer(inv -> { Thread.sleep(300); return null; })
                .when(emailService).sendAdminChatNotificationEmail(any(User.class), any(ChatSession.class));

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(5);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(post("/chat/request-admin/" + token)).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<Integer> r : results) {
            assertEquals(200, r.get(), "Un clic en double ne doit pas afficher d'erreur au visiteur");
        }
        pool.shutdown();

        String body = mockMvc.perform(get("/chat/history/" + token)).andReturn().getResponse().getContentAsString();
        long confirmations = objectMapper.readTree(body).path("data").path("messages").findValuesAsText("content").stream()
                .filter(c -> c.contains("notifié") || c.contains("notified"))
                .count();
        assertEquals(1, confirmations, "Le message de confirmation ne doit apparaître qu'une fois");
        // Les alertes partent en arrière-plan : on attend qu'elles soient envoyées
        verify(emailService, org.mockito.Mockito.timeout(5000).times(2))
                .sendAdminChatNotificationEmail(any(User.class), any(ChatSession.class));
        Thread.sleep(800);
        verify(emailService, times(2)).sendAdminChatNotificationEmail(any(User.class), any(ChatSession.class));
    }

    @Test
    void transferRequest_IsFastEvenWhenEmailIsSlow() throws Exception {
        String token = startChat("Rapide");
        org.mockito.Mockito.doAnswer(inv -> { Thread.sleep(2000); return null; })
                .when(emailService).sendAdminChatNotificationEmail(any(User.class), any(ChatSession.class));

        long t0 = System.currentTimeMillis();
        postJson("/chat/request-admin/" + token, Map.of(), null).andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue(elapsed < 1500, "La demande ne doit pas attendre l'envoi des e-mails (" + elapsed + " ms)");
    }
}
