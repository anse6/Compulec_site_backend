package com.compulec.backend.controller;

import com.compulec.backend.entity.ChatSession;
import com.compulec.backend.repository.ChatSessionRepository;
import com.compulec.backend.service.ChatAssistant;
import com.compulec.backend.service.ChatAssistantSelector;
import com.compulec.backend.service.EmailService;
import com.compulec.backend.support.TestAccounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat avec l'assistant IA (simulé) : langue, proposition de conseiller décidée par l'IA,
 * plafond de réponses, secours par mots-clés en cas d'erreur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.ai.max-replies-per-session=2")
public class ChatAiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestAccounts testAccounts;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @MockitoBean
    private ChatAssistantSelector assistant;

    @MockitoBean
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        testAccounts.deleteAllAccounts();
        when(assistant.isEnabled()).thenReturn(true);
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private String startChat(String language) throws Exception {
        String body = postJson("/chat/start", Map.of("visitorName", "Ana", "language", language))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("sessionToken").asText();
    }

    @Test
    void welcomeFollowsTheSiteLanguage() throws Exception {
        String token = startChat("en");
        mockMvc.perform(get("/chat/history/" + token))
                .andExpect(jsonPath("$.data.messages[0].content").value(org.hamcrest.Matchers.startsWith("Hello Ana!")));
    }

    @Test
    void aiAnswers_AndDecidesWhenToOfferAnAdvisor() throws Exception {
        when(assistant.reply(any(), anyList())).thenReturn(
                Optional.of(new ChatAssistant.Reply("Nous installons la vidéosurveillance.", false)),
                Optional.of(new ChatAssistant.Reply("Un conseiller peut vous faire un devis.", true)));
        String token = startChat("fr");

        postJson("/chat/message", Map.of("sessionToken", token, "message", "Faites-vous de la vidéosurveillance ?"))
                .andExpect(jsonPath("$.data.botReply").value("Nous installons la vidéosurveillance."))
                .andExpect(jsonPath("$.data.showAdminTransfer").value(false));
        postJson("/chat/message", Map.of("sessionToken", token, "message", "Combien pour 3 agences ?"))
                .andExpect(jsonPath("$.data.showAdminTransfer").value(true));

        ChatSession session = chatSessionRepository.findBySessionToken(token).orElseThrow();
        assertEquals(2, session.getAiReplyCount());

        // Plafond atteint (2 dans ce test) : plus d'appel à l'IA, on oriente vers un conseiller
        postJson("/chat/message", Map.of("sessionToken", token, "message", "Et la maintenance ?"))
                .andExpect(jsonPath("$.data.botReply").value(org.hamcrest.Matchers.containsString("conseiller")))
                .andExpect(jsonPath("$.data.showAdminTransfer").value(true));
    }

    @Test
    void aiFailure_FallsBackToKeywordBot() throws Exception {
        when(assistant.reply(any(), anyList())).thenReturn(Optional.empty());
        String token = startChat("fr");
        postJson("/chat/message", Map.of("sessionToken", token, "message", "Quels sont vos tarifs ?"))
                .andExpect(jsonPath("$.data.botReply").value(org.hamcrest.Matchers.containsString("tarifs")))
                .andExpect(jsonPath("$.data.showAdminTransfer").value(true));
    }
}
