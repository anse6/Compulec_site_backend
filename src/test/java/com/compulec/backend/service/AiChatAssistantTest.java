package com.compulec.backend.service;

import com.compulec.backend.entity.ChatMessage;
import com.compulec.backend.entity.ChatSenderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Assistant IA testé contre un faux serveur « API Anthropic » local :
 * vérifie la requête envoyée (modèle, cache, repli, historique) et l'interprétation des réponses.
 * Aucun appel réel, aucun coût.
 */
class AiChatAssistantTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<JsonNode> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastBetaHeader = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseText = "Bonjour ! COMPULEC propose la vidéosurveillance.";
    private volatile String stopReason = "end_turn";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().toString());
            lastBetaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            lastBody.set(json.readTree(exchange.getRequestBody()));
            String body = status == 200
                    ? json.writeValueAsString(json.createObjectNode()
                        .put("id", "msg_test").put("type", "message").put("role", "assistant")
                        .put("model", "claude-opus-5-5").put("stop_reason", stopReason).putNull("stop_sequence")
                        .<com.fasterxml.jackson.databind.node.ObjectNode>set("content", stopReason.equals("refusal")
                                ? json.createArrayNode()
                                : json.createArrayNode().add(json.createObjectNode().put("type", "text").put("text", responseText)))
                        .set("usage", json.createObjectNode().put("input_tokens", 10).put("output_tokens", 5)
                                .put("cache_read_input_tokens", 0).put("cache_creation_input_tokens", 4000)))
                    : "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private AiChatAssistant assistant(String apiKey) {
        AiChatAssistant assistant = new AiChatAssistant();
        ReflectionTestUtils.setField(assistant, "enabled", true);
        ReflectionTestUtils.setField(assistant, "apiKey", apiKey);
        ReflectionTestUtils.setField(assistant, "model", "claude-opus-5-5");
        ReflectionTestUtils.setField(assistant, "maxTokens", 4096L);
        ReflectionTestUtils.setField(assistant, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        assistant.init();
        return assistant;
    }

    private static ChatMessage msg(ChatSenderType type, String content) {
        return ChatMessage.builder().senderType(type).content(content).build();
    }

    private static List<ChatMessage> conversation(String lastVisitorMessage) {
        return List.of(
                msg(ChatSenderType.BOT, "Bonjour Paul ! Je suis l'assistant virtuel de COMPULEC."),
                msg(ChatSenderType.VISITOR, "Que faites-vous ?"),
                msg(ChatSenderType.BOT, "Nous proposons des solutions IT."),
                msg(ChatSenderType.VISITOR, lastVisitorMessage));
    }

    @Test
    void sendsTheExpectedRequest() {
        Optional<AiChatAssistant.Reply> reply = assistant("sk-test").reply(conversation("Do you install CCTV?"), "en");

        assertTrue(reply.isPresent());
        assertEquals("Bonjour ! COMPULEC propose la vidéosurveillance.", reply.get().text());
        assertFalse(reply.get().suggestTransfer());

        JsonNode body = lastBody.get();
        assertTrue(lastPath.get().startsWith("/v1/messages"), lastPath.get());
        assertEquals("claude-opus-5-5", body.path("model").asText());
        assertEquals("low", body.path("output_config").path("effort").asText());
        // Repli automatique en cas de refus
        assertEquals("default", body.path("fallbacks").asText());
        assertTrue(lastBetaHeader.get().contains("server-side-fallback-2026-07-01"), lastBetaHeader.get());
        // Base de connaissances mise en cache, avec la langue du site
        JsonNode system = body.path("system");
        assertEquals(2, system.size());
        assertEquals("ephemeral", system.get(1).path("cache_control").path("type").asText());
        assertTrue(system.get(1).path("text").asText().contains("<base_de_connaissances>"));
        assertTrue(system.get(1).path("text").asText().contains("anglais"));
        // Historique : commence par le visiteur (accueil du bot ignoré), se termine par le visiteur
        JsonNode messages = body.path("messages");
        assertEquals(3, messages.size());
        assertEquals("user", messages.get(0).path("role").asText());
        assertEquals("assistant", messages.get(1).path("role").asText());
        assertEquals("user", messages.get(2).path("role").asText());
    }

    @Test
    void transferMarkerIsRemovedAndFlagged() {
        responseText = "Un conseiller COMPULEC peut vous établir un devis.\n[[TRANSFERT]]";
        AiChatAssistant.Reply reply = assistant("sk-test").reply(conversation("Combien coûte un site web ?"), "fr").orElseThrow();
        assertTrue(reply.suggestTransfer());
        assertEquals("Un conseiller COMPULEC peut vous établir un devis.", reply.text());
    }

    @Test
    void refusalOffersAnAdvisor() {
        stopReason = "refusal";
        AiChatAssistant.Reply reply = assistant("sk-test").reply(conversation("…"), "fr").orElseThrow();
        assertTrue(reply.suggestTransfer());
        assertTrue(reply.text().contains("conseiller"));
    }

    @Test
    void apiErrorFallsBackToKeywords() {
        status = 400;
        assertTrue(assistant("sk-test").reply(conversation("Bonjour"), "fr").isEmpty());
    }

    @Test
    void withoutApiKeyTheAssistantIsDisabled() {
        AiChatAssistant assistant = assistant("");
        assertFalse(assistant.isEnabled());
        assertTrue(assistant.reply(conversation("Bonjour"), "fr").isEmpty());
        assertNull(lastBody.get(), "Aucun appel ne doit partir sans clé");
    }

    @Test
    void neverCallsWhenTheLastMessageIsNotFromTheVisitor() {
        List<ChatMessage> history = List.of(msg(ChatSenderType.VISITOR, "Bonjour"), msg(ChatSenderType.BOT, "Bonjour !"));
        assertTrue(assistant("sk-test").reply(history, "fr").isEmpty());
        assertNull(lastBody.get());
    }
}
